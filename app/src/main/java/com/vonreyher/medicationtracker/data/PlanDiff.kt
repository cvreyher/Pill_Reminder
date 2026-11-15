package com.vonreyher.medicationtracker.data

/** How a medication differs between two versions of a plan. */
data class PlanChange(val kind: Kind, val name: String, val details: List<String>) {
    enum class Kind(val label: String) { Added("Neu"), Changed("Geändert"), Removed("Abgesetzt") }
}

/**
 * Compares two plans entry by entry: medication is matched by PZN, or by name where there is none.
 * The result lists new, changed and discontinued medication.
 */
fun diffPlans(old: List<PlanEntry>, new: List<PlanEntry>): List<PlanChange> {
    fun key(entry: PlanEntry) = entry.pzn ?: entry.name.trim().lowercase()
    val oldByKey = old.associateBy(::key)
    val newKeys = new.map(::key).toSet()

    val changes = mutableListOf<PlanChange>()
    new.forEach { entry ->
        val previous = oldByKey[key(entry)]
        if (previous == null) {
            changes += PlanChange(PlanChange.Kind.Added, entry.name, listOfNotNull(entry.schedule ?: "Bei Bedarf"))
        } else {
            val details = buildList {
                if (previous.schedule != entry.schedule) {
                    add("Einnahme: ${previous.schedule ?: "Bei Bedarf"} → ${entry.schedule ?: "Bei Bedarf"}")
                }
                if (previous.strength != entry.strength) add("Stärke: ${previous.strength ?: "–"} → ${entry.strength ?: "–"}")
                if (previous.form != entry.form) add("Darreichungsform: ${previous.form ?: "–"} → ${entry.form ?: "–"}")
                if (previous.note != entry.note) add("Hinweis: ${entry.note ?: "entfernt"}")
                if (previous.reason != entry.reason) add("Grund: ${entry.reason ?: "entfernt"}")
            }
            if (details.isNotEmpty()) changes += PlanChange(PlanChange.Kind.Changed, entry.name, details)
        }
    }
    old.filter { key(it) !in newKeys }.forEach { entry ->
        changes += PlanChange(PlanChange.Kind.Removed, entry.name, emptyList())
    }
    return changes
}
