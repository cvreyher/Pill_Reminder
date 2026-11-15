package com.vonreyher.medicationtracker.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vonreyher.medicationtracker.IntakeEvent
import com.vonreyher.medicationtracker.Medication
import com.vonreyher.medicationtracker.placeholderName
import com.vonreyher.medicationtracker.reminders.ReminderScheduler
import com.vonreyher.medicationtracker.widget.MedicationWidgetUpdater
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.Executors

/** What the user told the app about a PZN. */
data class PznInfo(val name: String, val ingredient: String?, val form: String?)

/**
 * Holds people, their medication schedules, saved plans and the intake history, plus the app
 * settings, and persists everything as JSON in the app's files dir. Compose observes the snapshot
 * state directly; every change is written in the background.
 */
class MedicationStore private constructor(private val file: File) {

    val persons = mutableStateListOf<Person>()
    val medications = mutableStateListOf<Medication>()
    val plans = mutableStateListOf<MedicationPlan>()
    val history = mutableStateListOf<IntakeEvent>()

    var settings by mutableStateOf(AppSettings())
        private set

    /**
     * Names the user gave to PZNs. Medication plans usually carry only the PZN; the name comes from a
     * paid drug database we don't have, so it is remembered once entered and reused for later scans.
     */
    val pznCatalog = mutableStateMapOf<String, PznInfo>()

    /** Looks a PZN up in the imported drug database. */
    private var drugLookup: (String) -> Drug? = { null }

    /** Name for a PZN: what the user entered wins over the imported database. */
    private fun knownName(pzn: String): PznInfo? =
        pznCatalog[pzn] ?: drugLookup(pzn)?.let { drug ->
            PznInfo(listOfNotNull(drug.name, drug.strength?.takeIf { it !in drug.name }).joinToString(" "), null, drug.form)
        }

    private val writer = Executors.newSingleThreadExecutor()

    /** Called after every change, e.g. to refresh the widget and reschedule reminders. */
    private var onChanged: (() -> Unit)? = null

    /** The day the "taken" state of the medication refers to. */
    private var currentDay = LocalDate.now()

    init {
        load()
        val migrated = assignOrphansToSelf() or fillMissingCreatedAt()
        if (addMissingAsNeededMedications() || migrated) persist()
    }

    fun person(id: String): Person? = persons.firstOrNull { it.id == id }

    /** When a dose became part of the schedule; doses due before this moment never count as missed. */
    fun scheduledSince(medication: Medication): LocalDateTime? = medication.createdAt

    /** Medication that is currently scheduled: added by hand or from an active plan. */
    val activeMedications: List<Medication>
        get() {
            val inactivePlans = plans.filter { !it.active }.map { it.id }.toSet()
            return medications.filter { it.planId == null || it.planId !in inactivePlans }
        }

    /** Starts a new day: yesterday's intakes no longer count as taken. */
    fun ensureCurrentDay() {
        val today = LocalDate.now()
        if (today == currentDay) return
        currentDay = today
        if (medications.none { it.takenAt != null }) return
        medications.replaceAll { it.copy(takenAt = null) }
        persist()
    }

    // region Settings and people

    /** Finishes the welcome screen. In self mode the user is the only person. */
    fun completeOnboarding(mode: AppMode, firstName: String, lastName: String, firstPerson: Person?) {
        settings = settings.copy(mode = mode, firstName = firstName, lastName = lastName, onboardingDone = true)
        if (mode == AppMode.Self || persons.isEmpty()) syncSelfPerson()
        if (firstPerson != null) persons += firstPerson
        persist()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        syncSelfPerson()
        persist()
    }

    fun addPerson(firstName: String, lastName: String, birthDate: String?): Person {
        val person = Person(UUID.randomUUID().toString(), firstName.trim(), lastName.trim(), birthDate?.trim()?.ifBlank { null })
        persons += person
        persist()
        return person
    }

    fun updatePerson(person: Person) {
        val index = persons.indexOfFirst { it.id == person.id }
        if (index < 0) return
        persons[index] = person
        persist()
    }

    /** Deletes the person with all their plans and medication; their history is kept. */
    fun deletePerson(personId: String) {
        if (personId == Person.SELF_ID) return
        persons.removeAll { it.id == personId }
        plans.removeAll { it.personId == personId }
        medications.removeAll { it.personId == personId }
        persist()
    }

    /** The user's own person mirrors their name from the settings. */
    private fun syncSelfPerson() {
        val self = Person(Person.SELF_ID, settings.firstName.orEmpty(), settings.lastName.orEmpty())
        val index = persons.indexOfFirst { it.id == Person.SELF_ID }
        when {
            index >= 0 -> persons[index] = persons[index].copy(firstName = self.firstName, lastName = self.lastName)
            settings.mode == AppMode.Self || medications.any { it.personId == Person.SELF_ID } -> persons.add(0, self)
        }
    }

    /** Finds the person a scanned plan belongs to by name and birth date. */
    fun matchPerson(page: BmpPage): Person? {
        val given = page.patientGivenName?.trim()?.lowercase()
        val family = page.patientFamilyName?.trim()?.lowercase()
        return persons.firstOrNull { person ->
            val sameName = given != null && family != null &&
                person.firstName.trim().lowercase() == given && person.lastName.trim().lowercase() == family
            val sameBirthDate = page.patientBirthDate != null && person.birthDate == page.patientBirthDate
            (sameName && (person.birthDate == null || sameBirthDate)) ||
                (sameBirthDate && family != null && person.lastName.trim().lowercase() == family)
        }
    }

    // endregion

    // region Medication and intakes

    /** Adds the medication once per intake time; an empty list adds it as as-needed. */
    fun addMedication(template: Medication, times: List<LocalTime>) {
        var nextId = nextMedicationId()
        val now = LocalDateTime.now()
        if (times.isEmpty()) {
            medications += template.copy(id = nextId, time = null, createdAt = now)
        } else {
            times.distinct().sorted().forEach { time -> medications += template.copy(id = nextId++, time = time, createdAt = now) }
        }
        sortMedications()
        persist()
    }

    fun updateMedication(medication: Medication) {
        val index = medications.indexOfFirst { it.id == medication.id }
        if (index < 0) return
        medications[index] = medication
        sortMedications()
        persist()
    }

    /** Records an intake now and returns the history event, e.g. to undo it. */
    fun confirmIntake(medication: Medication): IntakeEvent {
        val now = LocalDateTime.now()
        val event = IntakeEvent(
            id = UUID.randomUUID().toString(),
            medicationId = medication.id,
            name = medication.name,
            dosage = medication.dosage,
            takenAt = now,
            scheduledTime = medication.time,
            personId = medication.personId
        )
        history += event
        replaceMedication(medication.id) { it.copy(takenAt = now.toLocalTime()) }
        persist()
        return event
    }

    /** Removes an intake from the history and restores the medication's state for today. */
    fun undoIntake(event: IntakeEvent) {
        history.removeAll { it.id == event.id }
        replaceMedication(event.medicationId) { it.copy(takenAt = latestIntakeToday(it.id)) }
        persist()
    }

    /** Removes today's latest intake of the medication, if any. */
    fun undoLatestIntakeToday(medication: Medication) {
        intakesToday(medication.id).maxByOrNull { it.takenAt }?.let(::undoIntake)
    }

    fun intakesToday(medicationId: Int): List<IntakeEvent> {
        val today = LocalDate.now()
        return history.filter { it.medicationId == medicationId && it.takenAt.toLocalDate() == today }
    }

    // endregion

    // region Plans

    /**
     * Saves the plan for the person and adds its doses; entries without fixed times become as-needed.
     * Plans in [replaces] are set inactive and point to the new plan, so the plan history stays intact.
     */
    fun savePlan(pages: Collection<BmpPage>, personId: String, replaces: List<String> = emptyList()): MedicationPlan {
        val first = pages.minBy { it.page }
        val plan = MedicationPlan(
            id = UUID.randomUUID().toString(),
            instanceId = first.instanceId,
            personId = personId,
            patientName = first.patientName,
            patientGivenName = first.patientGivenName,
            patientBirthDate = first.patientBirthDate,
            author = first.author,
            printedOn = first.printedOn,
            scannedAt = System.currentTimeMillis(),
            entries = pages.sortedBy { it.page }.flatMap { it.entries }
        )
        plans.replaceAll { old ->
            if (old.id in replaces) old.copy(active = false, deactivatedAt = plan.scannedAt, replacedBy = plan.id) else old
        }
        plans += plan

        var nextId = nextMedicationId()
        plan.entries.forEach { entry ->
            val intakes = entry.intakes
            if (intakes.isEmpty()) {
                medications += entry.toMedication(nextId++, entry.asNeededDosage, null, plan.id, personId).copy(createdAt = LocalDateTime.now())
            } else {
                intakes.forEach { (time, amount) ->
                    medications += entry.toMedication(nextId++, entry.dosageFor(amount), time, plan.id, personId).copy(createdAt = LocalDateTime.now())
                }
            }
        }
        sortMedications()
        persist()
        return plan
    }

    /** Fills in names remembered for PZN-only entries of a scanned page. */
    fun withKnownNames(page: BmpPage): BmpPage = page.copy(
        entries = page.entries.map { entry ->
            val pzn = entry.pzn
            val known = pzn?.let(::knownName)
            if (known != null && entry.name == placeholderName(pzn)) {
                entry.copy(
                    name = known.name,
                    ingredient = entry.ingredient ?: known.ingredient,
                    form = entry.form ?: known.form
                )
            } else {
                entry
            }
        }
    )

    /**
     * Saves an edited medication. The first time replaces the original dose, further times add doses.
     * Name, ingredient and form are remembered for the PZN and copied to every dose and plan entry with it.
     */
    fun editMedication(original: Medication, edited: Medication, times: List<LocalTime>) {
        val sortedTimes = times.distinct().sorted()
        replaceMedication(original.id) {
            edited.copy(id = original.id, time = sortedTimes.firstOrNull(), takenAt = it.takenAt, createdAt = it.createdAt)
        }
        var nextId = nextMedicationId()
        sortedTimes.drop(1).forEach { time ->
            medications += edited.copy(id = nextId++, time = time, takenAt = null, createdAt = LocalDateTime.now())
        }

        val pzn = edited.pzn
        if (pzn != null && edited.name != placeholderName(pzn)) {
            pznCatalog[pzn] = PznInfo(edited.name, edited.ingredient, edited.form)
            medications.replaceAll { medication ->
                if (medication.pzn == pzn && medication.id != original.id) {
                    medication.copy(name = edited.name, ingredient = edited.ingredient ?: medication.ingredient, form = edited.form ?: medication.form)
                } else {
                    medication
                }
            }
            plans.replaceAll { plan ->
                plan.copy(entries = plan.entries.map { entry ->
                    if (entry.pzn == pzn) entry.copy(name = edited.name, ingredient = entry.ingredient ?: edited.ingredient, form = entry.form ?: edited.form)
                    else entry
                })
            }
        }
        sortMedications()
        persist()
    }

    /** After a database import: names doses and plan entries that so far only had their PZN. */
    fun applyKnownNames(): Int {
        var renamed = 0
        medications.replaceAll { medication ->
            val pzn = medication.pzn
            val known = pzn?.takeIf { medication.hasPlaceholderName }?.let(::knownName) ?: return@replaceAll medication
            renamed++
            medication.copy(name = known.name, form = medication.form ?: known.form)
        }
        plans.replaceAll { plan ->
            plan.copy(entries = plan.entries.map { entry ->
                val pzn = entry.pzn
                val known = pzn?.takeIf { entry.name == placeholderName(it) }?.let(::knownName)
                if (known == null) entry else entry.copy(name = known.name, form = entry.form ?: known.form)
            })
        }
        if (renamed > 0) persist()
        return renamed
    }

    fun deleteMedication(medicationId: Int) {
        medications.removeAll { it.id == medicationId }
        persist()
    }

    /** Activates or deactivates a plan; activating can deactivate the person's other plans. */
    fun setPlanActive(planId: String, active: Boolean, deactivateOthers: Boolean = false) {
        val target = plans.firstOrNull { it.id == planId } ?: return
        val now = System.currentTimeMillis()
        plans.replaceAll { plan ->
            when {
                plan.id == planId -> plan.copy(active = active, deactivatedAt = if (active) null else now)
                active && deactivateOthers && plan.active && plan.personId == target.personId ->
                    plan.copy(active = false, deactivatedAt = now)
                else -> plan
            }
        }
        persist()
    }

    fun activePlansOf(personId: String): List<MedicationPlan> =
        plans.filter { it.active && it.personId == personId }

    /** The plan that [plan] replaced, if any. */
    fun predecessorOf(plan: MedicationPlan): MedicationPlan? = plans.firstOrNull { it.replacedBy == plan.id }

    fun deletePlan(planId: String) {
        val deleted = plans.firstOrNull { it.id == planId }
        plans.removeAll { it.id == planId }
        // The predecessor now points to whatever replaced the deleted plan.
        plans.replaceAll { if (it.replacedBy == planId) it.copy(replacedBy = deleted?.replacedBy) else it }
        medications.removeAll { it.planId == planId }
        persist()
    }

    // endregion

    /**
     * Doses saved before the creation time was tracked get one: when their plan was scanned, their first
     * recorded intake, or otherwise now – so earlier days are never shown as missed.
     */
    private fun fillMissingCreatedAt(): Boolean {
        if (medications.none { it.createdAt == null }) return false
        val now = LocalDateTime.now()
        medications.replaceAll { medication ->
            if (medication.createdAt != null) return@replaceAll medication
            val scanned = medication.planId?.let { id -> plans.firstOrNull { it.id == id } }?.scannedAt?.let {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault())
            }
            val firstIntake = history.filter { it.medicationId == medication.id }.minOfOrNull { it.takenAt }
            medication.copy(createdAt = listOfNotNull(scanned, firstIntake).minOrNull() ?: now)
        }
        return true
    }

    /** Data saved before people existed belongs to the user. */
    private fun assignOrphansToSelf(): Boolean {
        val hasData = medications.isNotEmpty() || plans.isNotEmpty() || history.isNotEmpty()
        if (!hasData || persons.any { it.id == Person.SELF_ID }) return false
        persons.add(0, Person(Person.SELF_ID, settings.firstName.orEmpty(), settings.lastName.orEmpty()))
        return true
    }

    /** Plans saved by earlier versions only added entries with fixed times; add the others as as-needed. */
    private fun addMissingAsNeededMedications(): Boolean {
        var nextId = nextMedicationId()
        var changed = false
        plans.forEach { plan ->
            plan.entries.filter { it.intakes.isEmpty() }.forEach { entry ->
                val exists = medications.any { it.planId == plan.id && it.name == entry.name }
                if (!exists) {
                    medications += entry.toMedication(nextId++, entry.asNeededDosage, null, plan.id, plan.personId)
                    changed = true
                }
            }
            // Doses saved before the plan details were copied onto them.
            medications.forEachIndexed { index, medication ->
                if (medication.planId != plan.id || medication.instructions != null || medication.reason != null) return@forEachIndexed
                val entry = plan.entries.firstOrNull { it.name == medication.name } ?: return@forEachIndexed
                if (entry.note == null && entry.reason == null && entry.ingredientText == null && entry.form == null) return@forEachIndexed
                medications[index] = medication.copy(
                    ingredient = entry.ingredientText,
                    form = entry.form,
                    instructions = entry.note,
                    reason = entry.reason
                )
                changed = true
            }
        }
        if (changed) sortMedications()
        return changed
    }

    private fun latestIntakeToday(medicationId: Int): LocalTime? =
        intakesToday(medicationId).maxOfOrNull { it.takenAt }?.toLocalTime()

    private inline fun replaceMedication(id: Int, transform: (Medication) -> Medication) {
        val index = medications.indexOfFirst { it.id == id }
        if (index >= 0) medications[index] = transform(medications[index])
    }

    private fun sortMedications() {
        medications.sortWith(compareBy(nullsLast()) { it.time })
    }

    private fun nextMedicationId(): Int = (medications.maxOfOrNull { it.id } ?: 0) + 1

    // region Persistence

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val today = LocalDate.now().toString()
            root.optJSONObject("settings")?.let { json ->
                val ema = json.optJSONObject("ema")
                settings = AppSettings(
                    mode = runCatching { AppMode.valueOf(json.optString("mode")) }.getOrDefault(AppMode.Self),
                    firstName = json.optStringOrNull("firstName"),
                    lastName = json.optStringOrNull("lastName"),
                    onboardingDone = json.optBoolean("onboardingDone"),
                    remindersEnabled = json.optBoolean("remindersEnabled", true),
                    lockScreenAlarm = json.optBoolean("lockScreenAlarm", false),
                    repeatReminders = json.optBoolean("repeatReminders", true),
                    repeatIntervalMinutes = json.optInt("repeatIntervalMinutes", 10),
                    soundWhenMuted = json.optBoolean("soundWhenMuted", true),
                    ema = EmaCredentials(
                        tokenUrl = ema?.optStringOrNull("tokenUrl").orEmpty(),
                        clientId = ema?.optStringOrNull("clientId").orEmpty(),
                        clientSecret = ema?.optStringOrNull("clientSecret").orEmpty(),
                        scope = ema?.optStringOrNull("scope").orEmpty()
                    )
                )
            }
            root.optJSONArray("persons")?.forEachObject { json ->
                persons += Person(
                    id = json.getString("id"),
                    firstName = json.optString("firstName"),
                    lastName = json.optString("lastName"),
                    birthDate = json.optStringOrNull("birthDate")
                )
            }
            root.optJSONArray("medications")?.forEachObject { json ->
                // Intake confirmations only count for the day they were made.
                val takenToday = json.optStringOrNull("takenOn") == today
                medications += Medication(
                    id = json.getInt("id"),
                    name = json.getString("name"),
                    dosage = json.getString("dosage"),
                    time = json.optStringOrNull("time")?.let(LocalTime::parse),
                    takenAt = json.optStringOrNull("takenAt")?.takeIf { takenToday }?.let(LocalTime::parse),
                    planId = json.optStringOrNull("planId"),
                    personId = json.optStringOrNull("personId") ?: Person.SELF_ID,
                    ingredient = json.optStringOrNull("ingredient"),
                    form = json.optStringOrNull("form"),
                    instructions = json.optStringOrNull("instructions"),
                    reason = json.optStringOrNull("reason"),
                    pzn = json.optStringOrNull("pzn"),
                    createdAt = json.optStringOrNull("createdAt")?.let(LocalDateTime::parse)
                )
            }
            root.optJSONArray("plans")?.forEachObject { json -> plans += json.toPlan() }
            root.optJSONObject("pznCatalog")?.let { catalog ->
                catalog.keys().forEach { pzn ->
                    val json = catalog.getJSONObject(pzn)
                    pznCatalog[pzn] = PznInfo(json.getString("name"), json.optStringOrNull("ingredient"), json.optStringOrNull("form"))
                }
            }
            root.optJSONArray("history")?.forEachObject { json ->
                history += IntakeEvent(
                    id = json.getString("id"),
                    medicationId = json.getInt("medicationId"),
                    name = json.getString("name"),
                    dosage = json.getString("dosage"),
                    takenAt = LocalDateTime.parse(json.getString("takenAt")),
                    scheduledTime = json.optStringOrNull("scheduledTime")?.let(LocalTime::parse),
                    personId = json.optStringOrNull("personId") ?: Person.SELF_ID
                )
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Could not read saved data", e)
        }
    }

    private fun persist() {
        val today = LocalDate.now().toString()
        val json = JSONObject()
            .put("settings", JSONObject()
                .put("mode", settings.mode.name)
                .putOpt("firstName", settings.firstName)
                .putOpt("lastName", settings.lastName)
                .put("onboardingDone", settings.onboardingDone)
                .put("remindersEnabled", settings.remindersEnabled)
                .put("lockScreenAlarm", settings.lockScreenAlarm)
                .put("repeatReminders", settings.repeatReminders)
                .put("repeatIntervalMinutes", settings.repeatIntervalMinutes)
                .put("soundWhenMuted", settings.soundWhenMuted)
                .put("ema", JSONObject()
                    .put("tokenUrl", settings.ema.tokenUrl)
                    .put("clientId", settings.ema.clientId)
                    .put("clientSecret", settings.ema.clientSecret)
                    .put("scope", settings.ema.scope)))
            .put("persons", JSONArray().apply {
                persons.forEach { person ->
                    put(JSONObject()
                        .put("id", person.id)
                        .put("firstName", person.firstName)
                        .put("lastName", person.lastName)
                        .putOpt("birthDate", person.birthDate))
                }
            })
            .put("medications", JSONArray().apply {
                medications.forEach { medication ->
                    put(JSONObject()
                        .put("id", medication.id)
                        .put("name", medication.name)
                        .put("dosage", medication.dosage)
                        .putOpt("time", medication.time?.toString())
                        .putOpt("takenAt", medication.takenAt?.toString())
                        .putOpt("takenOn", medication.takenAt?.let { today })
                        .putOpt("planId", medication.planId)
                        .put("personId", medication.personId)
                        .putOpt("ingredient", medication.ingredient)
                        .putOpt("form", medication.form)
                        .putOpt("instructions", medication.instructions)
                        .putOpt("reason", medication.reason)
                        .putOpt("pzn", medication.pzn)
                        .putOpt("createdAt", medication.createdAt?.toString()))
                }
            })
            .put("plans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
            .put("pznCatalog", JSONObject().apply {
                pznCatalog.forEach { (pzn, info) ->
                    put(pzn, JSONObject().put("name", info.name).putOpt("ingredient", info.ingredient).putOpt("form", info.form))
                }
            })
            .put("history", JSONArray().apply {
                history.forEach { event ->
                    put(JSONObject()
                        .put("id", event.id)
                        .put("medicationId", event.medicationId)
                        .put("name", event.name)
                        .put("dosage", event.dosage)
                        .put("takenAt", event.takenAt.toString())
                        .putOpt("scheduledTime", event.scheduledTime?.toString())
                        .put("personId", event.personId))
                }
            })
            .toString()

        writer.execute {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json)
            if (!temp.renameTo(file)) Log.e(TAG, "Could not save data")
            onChanged?.invoke()
        }
    }

    // endregion

    companion object {
        private const val TAG = "MedicationStore"

        @Volatile
        private var instance: MedicationStore? = null

        fun get(context: Context): MedicationStore =
            instance ?: synchronized(this) {
                instance ?: MedicationStore(File(context.applicationContext.filesDir, "medication_data.json"))
                    .also { store ->
                        val appContext = context.applicationContext
                        store.drugLookup = { pzn -> DrugDatabase.get(appContext).find(pzn) }
                        store.onChanged = {
                            MedicationWidgetUpdater.update(appContext)
                            ReminderScheduler.sync(appContext)
                        }
                        instance = store
                    }
            }.also { it.ensureCurrentDay() }
    }
}

private fun MedicationPlan.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .putOpt("instanceId", instanceId)
    .put("personId", personId)
    .putOpt("patientName", patientName)
    .putOpt("patientGivenName", patientGivenName)
    .putOpt("patientBirthDate", patientBirthDate)
    .putOpt("author", author)
    .putOpt("printedOn", printedOn)
    .put("scannedAt", scannedAt)
    .put("active", active)
    .putOpt("deactivatedAt", deactivatedAt)
    .putOpt("replacedBy", replacedBy)
    .put("entries", JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject()
                .put("name", entry.name)
                .putOpt("pzn", entry.pzn)
                .putOpt("strength", entry.strength)
                .putOpt("ingredient", entry.ingredient)
                .putOpt("form", entry.form)
                .putOpt("morning", entry.morning)
                .putOpt("noon", entry.noon)
                .putOpt("evening", entry.evening)
                .putOpt("night", entry.night)
                .putOpt("freeTextDosage", entry.freeTextDosage)
                .putOpt("unit", entry.unit)
                .putOpt("note", entry.note)
                .putOpt("reason", entry.reason)
                .putOpt("section", entry.section))
        }
    })

private fun JSONObject.toPlan(): MedicationPlan = MedicationPlan(
    id = getString("id"),
    instanceId = optStringOrNull("instanceId"),
    personId = optStringOrNull("personId") ?: Person.SELF_ID,
    patientName = optStringOrNull("patientName"),
    patientGivenName = optStringOrNull("patientGivenName"),
    patientBirthDate = optStringOrNull("patientBirthDate"),
    author = optStringOrNull("author"),
    printedOn = optStringOrNull("printedOn"),
    scannedAt = getLong("scannedAt"),
    active = optBoolean("active", true),
    deactivatedAt = if (has("deactivatedAt")) getLong("deactivatedAt") else null,
    replacedBy = optStringOrNull("replacedBy"),
    entries = buildList {
        optJSONArray("entries")?.forEachObject { json ->
            add(PlanEntry(
                name = json.getString("name"),
                pzn = json.optStringOrNull("pzn"),
                strength = json.optStringOrNull("strength"),
                ingredient = json.optStringOrNull("ingredient"),
                form = json.optStringOrNull("form"),
                morning = json.optStringOrNull("morning"),
                noon = json.optStringOrNull("noon"),
                evening = json.optStringOrNull("evening"),
                night = json.optStringOrNull("night"),
                freeTextDosage = json.optStringOrNull("freeTextDosage"),
                unit = json.optStringOrNull("unit"),
                note = json.optStringOrNull("note"),
                reason = json.optStringOrNull("reason"),
                section = json.optStringOrNull("section")
            ))
        }
    }
)

private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
    for (i in 0 until length()) action(getJSONObject(i))
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).ifEmpty { null }
