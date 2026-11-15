package com.vonreyher.medicationtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.BmpPage
import com.vonreyher.medicationtracker.data.EmaCredentials
import com.vonreyher.medicationtracker.data.MedicationPlan
import com.vonreyher.medicationtracker.data.Person
import com.vonreyher.medicationtracker.data.PlanChange
import com.vonreyher.medicationtracker.data.PlanEntry
import com.vonreyher.medicationtracker.data.diffPlans
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.GERMANY).format(Date(millis))

fun MedicationPlan.title(): String =
    patientName?.let { "Plan für $it" } ?: printedOn?.let { "Plan vom $it" } ?: "Medikationsplan"

/** Active plans first, below them the plan history with replaced and deactivated plans. */
@Composable
fun PlansScreen(
    plans: List<MedicationPlan>,
    persons: List<Person>,
    careMode: Boolean,
    contentPadding: PaddingValues,
    onPlanClick: (MedicationPlan) -> Unit
) {
    if (plans.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Noch keine Pläne. Scanne den Code auf einem Bundeseinheitlichen Medikationsplan, um ihn hier zu speichern.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val active = plans.filter { it.active }.sortedByDescending { it.scannedAt }
    val inactive = plans.filter { !it.active }.sortedByDescending { it.deactivatedAt ?: it.scannedAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        )
    ) {
        item { PlanSectionTitle("Aktive Pläne") }
        if (active.isEmpty()) {
            item {
                Text(
                    "Kein Plan ist aktiv – die Medikamente der Pläne erscheinen gerade nicht im Tagesplan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        items(active, key = { it.id }) { plan -> PlanRow(plan, plans, persons, careMode, onPlanClick) }

        if (inactive.isNotEmpty()) {
            item { PlanSectionTitle("Planverlauf") }
            items(inactive, key = { it.id }) { plan -> PlanRow(plan, plans, persons, careMode, onPlanClick) }
        }
    }
}

@Composable
private fun PlanRow(
    plan: MedicationPlan,
    allPlans: List<MedicationPlan>,
    persons: List<Person>,
    careMode: Boolean,
    onPlanClick: (MedicationPlan) -> Unit
) {
    val person = persons.firstOrNull { it.id == plan.personId }
    val successor = plan.replacedBy?.let { id -> allPlans.firstOrNull { it.id == id } }
    ListItem(
        modifier = Modifier.clickable { onPlanClick(plan) },
        overlineContent = { Text("Gescannt am ${formatDate(plan.scannedAt)}") },
        headlineContent = { Text(plan.title()) },
        supportingContent = {
            Column {
                Text(listOfNotNull(plan.author, "${plan.entries.size} Medikamente").joinToString(" · "))
                when {
                    plan.active -> Unit
                    successor != null -> Text("Ersetzt am ${formatDate(plan.deactivatedAt ?: successor.scannedAt)}")
                    else -> Text("Inaktiv seit ${plan.deactivatedAt?.let(::formatDate) ?: "–"}")
                }
            }
        },
        leadingContent = {
            if (careMode && person != null) PersonAvatar(person)
            else Icon(painterResource(R.drawable.ic_description), contentDescription = null)
        },
        trailingContent = { StatusPill(plan.active) },
        colors = if (plan.active) ListItemDefaults.colors() else ListItemDefaults.colors(
            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun StatusPill(active: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            if (active) "Aktiv" else "Inaktiv",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PlanSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    plan: MedicationPlan,
    predecessor: MedicationPlan?,
    successor: MedicationPlan?,
    otherActivePlans: List<MedicationPlan>,
    emaCredentials: EmaCredentials,
    onClose: () -> Unit,
    onSetActive: (active: Boolean, deactivateOthers: Boolean) -> Unit,
    onOpenPlan: (MedicationPlan) -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onClose)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmActivate by rememberSaveable { mutableStateOf(false) }
    var infoFor by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.title()) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Schließen")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Plan löschen")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                ListItem(
                    modifier = Modifier.toggleable(
                        value = plan.active,
                        role = Role.Switch,
                        onValueChange = { active ->
                            if (active && otherActivePlans.isNotEmpty()) confirmActivate = true
                            else onSetActive(active, false)
                        }
                    ),
                    headlineContent = { Text("Plan aktiv") },
                    supportingContent = {
                        Text(
                            if (plan.active) "Die Medikamente stehen im Tagesplan und werden erinnert."
                            else "Der Plan ist im Verlauf gespeichert. Seine Medikamente erscheinen nicht im Tagesplan."
                        )
                    },
                    trailingContent = { Switch(checked = plan.active, onCheckedChange = null) }
                )
            }
            if (successor != null) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { onOpenPlan(successor) },
                        overlineContent = { Text("Ersetzt durch") },
                        headlineContent = { Text("Plan vom ${successor.printedOn ?: formatDate(successor.scannedAt)}") },
                        supportingContent = { Text("Gescannt am ${formatDate(successor.scannedAt)}") }
                    )
                }
            }
            if (predecessor != null) {
                item {
                    ListItem(
                        modifier = Modifier.clickable { onOpenPlan(predecessor) },
                        overlineContent = { Text("Ersetzt den vorherigen Plan") },
                        headlineContent = { Text("Plan vom ${predecessor.printedOn ?: formatDate(predecessor.scannedAt)}") },
                        supportingContent = { Text("Gescannt am ${formatDate(predecessor.scannedAt)}") }
                    )
                }
                planChanges(diffPlans(predecessor.entries, plan.entries))
            }
            item { HorizontalDivider() }
            planHeader(plan.patientName, plan.patientBirthDate, plan.author, plan.printedOn)
            planEntries(plan.entries, onEntryClick = { infoFor = it.name })
        }
    }

    infoFor?.let { name ->
        DrugInfoSheet(medicationName = name, credentials = emaCredentials, onDismiss = { infoFor = null })
    }

    if (confirmActivate) {
        AlertDialog(
            onDismissRequest = { confirmActivate = false },
            title = { Text("Anderen Plan deaktivieren?") },
            text = {
                Text(
                    "Für diese Person ist bereits ein anderer Plan aktiv. Sollen dessen Medikamente aus dem " +
                        "Tagesplan genommen werden, damit nichts doppelt erscheint?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmActivate = false
                    onSetActive(true, true)
                }) { Text("Ja, deaktivieren") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmActivate = false
                    onSetActive(true, false)
                }) { Text("Beide aktiv lassen") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text("Plan löschen?") },
            text = {
                Text(
                    "Der Plan und seine Medikamente werden endgültig entfernt. Wenn du ihn nur nicht mehr " +
                        "brauchst, stelle ihn lieber auf inaktiv – dann bleibt er im Planverlauf."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}

/** Shows the scanned pages of a plan before it is saved, including who it is for and what it replaces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreviewScreen(
    pages: Map<Int, BmpPage>,
    careMode: Boolean,
    persons: List<Person>,
    suggestedPersonId: String?,
    activePlansOf: (personId: String) -> List<MedicationPlan>,
    onScanNextPage: () -> Unit,
    onSave: (owner: PlanOwner, replaces: List<String>) -> Unit,
    onDiscard: () -> Unit
) {
    BackHandler(onBack = onDiscard)

    val first = pages.values.minBy { it.page }
    val pageCount = first.pageCount
    val complete = pages.size >= pageCount
    val entries = pages.values.sortedBy { it.page }.flatMap { it.entries }
    val patientAsPerson = first.patientGivenName?.let { given ->
        Person(NewPersonId, given, first.patientFamilyName.orEmpty(), first.patientBirthDate)
    }
    var ownerId by rememberSaveable {
        mutableStateOf(
            when {
                !careMode -> Person.SELF_ID
                suggestedPersonId != null -> suggestedPersonId
                patientAsPerson != null -> NewPersonId
                else -> persons.firstOrNull()?.id
            }
        )
    }
    var replace by rememberSaveable { mutableStateOf(true) }
    val currentPlans = ownerId?.takeIf { it != NewPersonId }?.let(activePlansOf).orEmpty()
    val latest = currentPlans.maxByOrNull { it.scannedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gescannter Plan") },
                navigationIcon = {
                    IconButton(onClick = onDiscard) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Verwerfen")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (!complete) {
                    OutlinedButton(onClick = onScanNextPage) { Text("Nächste Seite scannen") }
                }
                Button(
                    onClick = {
                        val id = ownerId ?: return@Button
                        val owner = if (id == NewPersonId) PlanOwner.New(patientAsPerson!!) else PlanOwner.Existing(id)
                        onSave(owner, if (replace) currentPlans.map { it.id } else emptyList())
                    },
                    enabled = ownerId != null
                ) { Text(if (replace && currentPlans.isNotEmpty()) "Plan ersetzen" else "Plan speichern") }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            if (pageCount > 1) {
                item {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (complete) "Alle $pageCount Seiten gescannt"
                            else "Seite ${pages.keys.sorted().joinToString()} von $pageCount gescannt",
                            style = MaterialTheme.typography.titleSmall
                        )
                        LinearProgressIndicator(
                            progress = { pages.size.toFloat() / pageCount },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (careMode) {
                item { PlanSectionTitle("Für wen ist dieser Plan?") }
                items(persons, key = { it.id }) { person ->
                    OwnerOption(
                        person = person,
                        label = person.fullName.ifBlank { "Ohne Namen" },
                        supporting = when {
                            person.id == suggestedPersonId -> "Passt zum Patienten auf dem Plan"
                            else -> person.birthDate?.let { "geb. $it" }
                        },
                        selected = ownerId == person.id,
                        onSelect = { ownerId = person.id }
                    )
                }
                if (patientAsPerson != null && suggestedPersonId == null) {
                    item {
                        OwnerOption(
                            person = patientAsPerson,
                            label = "Neue Person: ${patientAsPerson.fullName}",
                            supporting = patientAsPerson.birthDate?.let { "geb. $it" } ?: "Aus dem Plan übernehmen",
                            selected = ownerId == NewPersonId,
                            onSelect = { ownerId = NewPersonId }
                        )
                    }
                }
            }
            if (latest != null) {
                item {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        ListItem(
                            modifier = Modifier.toggleable(value = replace, role = Role.Switch, onValueChange = { replace = it }),
                            headlineContent = { Text("Bisherigen Plan ersetzen") },
                            supportingContent = {
                                Text(
                                    if (replace) "Der Plan vom ${latest.printedOn ?: formatDate(latest.scannedAt)} wird inaktiv und bleibt im Planverlauf."
                                    else "Beide Pläne bleiben aktiv."
                                )
                            },
                            trailingContent = { Switch(checked = replace, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                if (replace) planChanges(diffPlans(latest.entries, entries))
            }
            val unnamed = entries.count { entry -> entry.pzn != null && entry.name == "PZN ${entry.pzn}" }
            if (unnamed > 0) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text(
                            text = if (unnamed == 1) {
                                "Ein Medikament ist auf dem Plan nur mit seiner PZN hinterlegt – so sieht es der " +
                                    "Medikationsplan vor. Den Namen kannst du nach dem Speichern über „Bearbeiten“ ergänzen " +
                                    "oder per Arzneimitteldatenbank; die App merkt ihn sich für künftige Pläne."
                            } else {
                                "$unnamed Medikamente sind auf dem Plan nur mit ihrer PZN hinterlegt – so sieht es der " +
                                    "Medikationsplan vor. Die Namen kannst du nach dem Speichern über „Bearbeiten“ ergänzen " +
                                    "oder per Arzneimitteldatenbank; die App merkt sie sich für künftige Pläne."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            planHeader(first.patientName, first.patientBirthDate, first.author, first.printedOn)
            planEntries(entries)
        }
    }
}

/** Who a scanned plan is saved for. */
sealed interface PlanOwner {
    data class Existing(val personId: String) : PlanOwner
    data class New(val person: Person) : PlanOwner
}

private const val NewPersonId = "new-person"

@Composable
private fun OwnerOption(person: Person, label: String, supporting: String?, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { PersonAvatar(person) },
        trailingContent = { RadioButton(selected = selected, onClick = null) }
    )
}

/** New, changed and discontinued medication compared to the previous plan. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.planChanges(changes: List<PlanChange>) {
    item { PlanSectionTitle("Änderungen zum vorherigen Plan") }
    if (changes.isEmpty()) {
        item {
            Text(
                "Keine Änderungen an den Medikamenten.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        return
    }
    items(changes) { change ->
        val colorScheme = MaterialTheme.colorScheme
        val (container, content) = when (change.kind) {
            PlanChange.Kind.Added -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
            PlanChange.Kind.Changed -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
            PlanChange.Kind.Removed -> colorScheme.errorContainer to colorScheme.onErrorContainer
        }
        ListItem(
            headlineContent = { Text(change.name) },
            supportingContent = if (change.details.isEmpty()) null else ({
                Column { change.details.forEach { Text(it) } }
            }),
            leadingContent = {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = container, contentColor = content) {
                    Text(
                        change.kind.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        )
    }
}

private fun LazyListScope.planHeader(
    patientName: String?,
    birthDate: String?,
    author: String?,
    printedOn: String?
) {
    if (patientName != null) {
        item {
            ListItem(
                overlineContent = { Text("Patient") },
                headlineContent = { Text(patientName) },
                supportingContent = birthDate?.let { { Text("Geboren am $it") } }
            )
        }
    }
    if (author != null) {
        item {
            ListItem(
                overlineContent = { Text("Ausgestellt von") },
                headlineContent = { Text(author) },
                supportingContent = printedOn?.let { { Text("Ausgedruckt am $it") } }
            )
        }
    }
    item { HorizontalDivider() }
}

private fun LazyListScope.planEntries(entries: List<PlanEntry>, onEntryClick: ((PlanEntry) -> Unit)? = null) {
    if (entries.isEmpty()) {
        item {
            Text(
                text = "Dieser Plan enthält keine Medikamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    items(entries) { entry ->
        ListItem(
            modifier = if (onEntryClick != null) Modifier.clickable { onEntryClick(entry) } else Modifier,
            overlineContent = entry.section?.let { { Text(it) } },
            headlineContent = { Text(entry.name) },
            supportingContent = {
                Column {
                    entry.ingredientText?.let { Text("Wirkstoff: $it") }
                    val details = listOfNotNull(entry.strength.takeIf { entry.ingredientText == null }, entry.form, entry.pzn?.let { "PZN $it" })
                    if (details.isNotEmpty()) Text(details.joinToString(" · "))
                    listOfNotNull(entry.reason, entry.note).forEach { Text(it) }
                }
            },
            leadingContent = {
                Icon(painterResource(R.drawable.ic_medication), contentDescription = null)
            },
            trailingContent = {
                Text(
                    text = entry.schedule ?: "Bei Bedarf",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}
