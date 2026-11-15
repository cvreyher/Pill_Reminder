package com.vonreyher.medicationtracker

import com.vonreyher.medicationtracker.data.Person
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Medication(
    val id: Int,
    val name: String,
    val dosage: String,
    /** Scheduled intake time, or null for medication taken as needed. */
    val time: LocalTime?,
    /** Time of today's (latest) intake. */
    val takenAt: LocalTime? = null,
    /** Id of the [com.vonreyher.medicationtracker.data.MedicationPlan] this dose comes from, if any. */
    val planId: String? = null,
    /** The [com.vonreyher.medicationtracker.data.Person] taking this medication. */
    val personId: String = Person.SELF_ID,
    /** Active ingredient(s) with strength, e.g. "Metformin 500 mg"; [name] is the trade name. */
    val ingredient: String? = null,
    val form: String? = null,
    /** Instructions from the plan, e.g. "auf nüchternen Magen". */
    val instructions: String? = null,
    /** Reason for the medication, e.g. "Blutdruck". */
    val reason: String? = null,
    /** Pharmazentralnummer of the package. */
    val pzn: String? = null,
    /** When the dose was added; only doses due after this moment can count as missed. */
    val createdAt: LocalDateTime? = null
) {
    val asNeeded: Boolean get() = time == null

    /** Plans that only carry the PZN give the medication a placeholder name until it is named. */
    val hasPlaceholderName: Boolean get() = pzn != null && name == placeholderName(pzn)

    /** Scheduled doses are done once taken; as-needed medication can be taken any number of times. */
    val taken: Boolean get() = !asNeeded && takenAt != null
}

/** One confirmed intake, kept for the history. */
data class IntakeEvent(
    val id: String,
    val medicationId: Int,
    val name: String,
    val dosage: String,
    val takenAt: LocalDateTime,
    val scheduledTime: LocalTime?,
    val personId: String = Person.SELF_ID
)

/** Name shown for medication known only by its PZN. */
fun placeholderName(pzn: String) = "PZN $pzn"

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalTime.formatted(): String = format(TimeFormatter)

/** Formats a positive number of minutes as "45 min" or "2 Std. 15 min". */
fun formatMinutes(minutes: Long): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "$hours Std." else "$hours Std. $rest min"
}
