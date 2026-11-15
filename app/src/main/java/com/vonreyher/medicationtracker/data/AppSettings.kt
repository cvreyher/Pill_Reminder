package com.vonreyher.medicationtracker.data

/** Whether the app tracks the user's own medication or that of several people they care for. */
enum class AppMode { Self, Care }

data class AppSettings(
    val mode: AppMode = AppMode.Self,
    /** The user's own name; in care mode this is the caregiver. */
    val firstName: String? = null,
    val lastName: String? = null,
    /** Set once the welcome screen was completed. */
    val onboardingDone: Boolean = false,
    /** Send a notification at every scheduled intake time. */
    val remindersEnabled: Boolean = true,
    /** On a locked phone, show the reminder full screen like an alarm clock. */
    val lockScreenAlarm: Boolean = false,
    /** Keep reminding about a dose that hasn't been confirmed. */
    val repeatReminders: Boolean = true,
    /** Minutes between repeated reminders. */
    val repeatIntervalMinutes: Int = 10,
    /** Play the reminder sound on the alarm stream, so it is audible even when the phone is muted. */
    val soundWhenMuted: Boolean = true,
    val ema: EmaCredentials = EmaCredentials()
)

/**
 * OAuth2 client credentials for the EMA Product Management Service API. They are issued per
 * registered user by the EMA and are entered in the settings, so they never ship inside the app.
 */
data class EmaCredentials(
    val tokenUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scope: String = ""
) {
    val isComplete: Boolean
        get() = tokenUrl.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank()
}

/** Someone whose medication is tracked. In self mode there is exactly one person: the user. */
data class Person(
    val id: String,
    val firstName: String,
    val lastName: String,
    /** Birth date as dd.MM.yyyy, the format used on the medication plan. */
    val birthDate: String? = null
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    val initials: String
        get() = listOf(firstName, lastName).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
            .ifEmpty { "?" }

    companion object {
        /** Id of the user's own person, used in self mode. */
        const val SELF_ID = "self"
    }
}
