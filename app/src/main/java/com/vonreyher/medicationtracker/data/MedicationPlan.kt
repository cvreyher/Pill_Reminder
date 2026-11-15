package com.vonreyher.medicationtracker.data

import java.time.LocalTime

/** A saved medication plan, e.g. a scanned Bundeseinheitlicher Medikationsplan (BMP). */
data class MedicationPlan(
    val id: String,
    /** BMP instance id (attribute `U`), shared by all pages of one printed plan. */
    val instanceId: String?,
    /** The [Person] this plan belongs to. */
    val personId: String = Person.SELF_ID,
    val patientName: String?,
    val patientGivenName: String?,
    val patientBirthDate: String?,
    val author: String?,
    val printedOn: String?,
    val scannedAt: Long,
    val entries: List<PlanEntry>,
    /** Only active plans put their medication into the schedule; inactive ones are kept as history. */
    val active: Boolean = true,
    /** When the plan was last set inactive. */
    val deactivatedAt: Long? = null,
    /** The newer plan that replaced this one, if any. */
    val replacedBy: String? = null
)

/** One medication line of a plan. Dosage values are kept as printed, e.g. "1", "1/2" or "0,5". */
data class PlanEntry(
    val name: String,
    val pzn: String? = null,
    val strength: String? = null,
    /** Active ingredient name(s), e.g. "Metformin". */
    val ingredient: String? = null,
    val form: String? = null,
    val morning: String? = null,
    val noon: String? = null,
    val evening: String? = null,
    val night: String? = null,
    val freeTextDosage: String? = null,
    val unit: String? = null,
    val note: String? = null,
    val reason: String? = null,
    val section: String? = null
) {
    /** Scheduled intakes as (time, amount); empty for as-needed or free-text dosages. */
    val intakes: List<Pair<LocalTime, String>>
        get() = listOf(
            MorningTime to morning,
            NoonTime to noon,
            EveningTime to evening,
            NightTime to night
        ).mapNotNull { (time, amount) -> amount?.takeIf { it.isTaken() }?.let { time to it } }

    /** The schedule the way it is printed on the plan, e.g. "1-0-1-0". */
    val schedule: String?
        get() = freeTextDosage
            ?: listOf(morning, noon, evening, night)
                .takeIf { slots -> slots.any { it != null } }
                ?.joinToString("-") { it ?: "0" }

    fun dosageFor(amount: String): String =
        listOfNotNull(
            listOfNotNull(amount, unit).joinToString(" "),
            strength
        ).joinToString(" · ")

    /** Ingredient with strength, e.g. "Metformin 500 mg", if it differs from the name. */
    val ingredientText: String?
        get() = ingredient?.let { listOfNotNull(it, strength).joinToString(" ") }?.takeIf { it != name }

    /** Copies the descriptive plan data onto a scheduled dose of this entry. */
    fun toMedication(id: Int, dosage: String, time: LocalTime?, planId: String, personId: String) =
        com.vonreyher.medicationtracker.Medication(
            id = id,
            name = name,
            dosage = dosage,
            time = time,
            planId = planId,
            personId = personId,
            ingredient = ingredientText,
            form = form,
            instructions = note,
            reason = reason,
            pzn = pzn
        )

    /** Dosage text for entries without fixed intake times (as needed or free-text instructions). */
    val asNeededDosage: String
        get() = listOfNotNull(strength, form, freeTextDosage)
            .joinToString(" · ")
            .ifBlank { "Bei Bedarf" }

    companion object {
        val MorningTime: LocalTime = LocalTime.of(8, 0)
        val NoonTime: LocalTime = LocalTime.of(12, 0)
        val EveningTime: LocalTime = LocalTime.of(18, 0)
        val NightTime: LocalTime = LocalTime.of(22, 0)
    }
}

private fun String.isTaken(): Boolean = isNotBlank() && trim().let { it != "0" && it != "0,0" && it != "0.0" }
