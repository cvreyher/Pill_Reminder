package com.vonreyher.medicationtracker.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vonreyher.medicationtracker.Medication
import com.vonreyher.medicationtracker.R
import com.vonreyher.medicationtracker.data.AppMode
import com.vonreyher.medicationtracker.data.MedicationStore
import com.vonreyher.medicationtracker.data.Person
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules one alarm for the next intake time; when it fires, every dose due at that time gets a
 * high-priority notification and the following alarm is scheduled.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "intake_reminders"
    /** Same reminders, but the sound uses the alarm stream and plays even when the phone is muted. */
    const val ALARM_CHANNEL_ID = "intake_reminders_alarm"
    private const val NEXT_ALARM_REQUEST = 0
    /** Request codes of the per-dose follow-up alarm (repeat or snooze), offset by the medication id. */
    private const val FOLLOW_UP_REQUEST_OFFSET = 100_000
    private const val SNOOZE_ACTION_REQUEST_OFFSET = 200_000
    private const val DEFAULT_SNOOZE_MINUTES = 10L

    internal const val ACTION_DUE = "com.vonreyher.medicationtracker.action.DOSE_DUE"
    internal const val ACTION_SNOOZE = "com.vonreyher.medicationtracker.action.SNOOZE"
    internal const val ACTION_SNOOZE_DUE = "com.vonreyher.medicationtracker.action.SNOOZE_DUE"
    internal const val EXTRA_TIME = "time"
    internal const val EXTRA_MEDICATION_ID = "medicationId"
    internal const val EXTRA_DUE_DAY = "dueDay"

    fun createChannel(context: Context) {
        val normal = NotificationChannel(
            CHANNEL_ID,
            "Einnahme-Erinnerungen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Erinnert pünktlich an jede geplante Einnahme. Folgt der Lautlos-Einstellung des Handys."
            enableVibration(true)
        }
        val alarm = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Einnahme-Erinnerungen (auch bei Stummschaltung)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Erinnert pünktlich an jede Einnahme – mit Ton auch dann, wenn das Handy stumm geschaltet ist."
            enableVibration(true)
            // Alarm sounds are not silenced by the ringer switch; they follow the alarm volume.
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(normal, alarm))
    }

    /** Brings alarms and notifications in line with the current schedule. */
    fun sync(context: Context) {
        val store = MedicationStore.get(context)
        val notifications = NotificationManagerCompat.from(context)

        // Doses confirmed in the meantime, or from plans set inactive, no longer need their reminder.
        val active = store.activeMedications.map { it.id }.toSet()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        store.medications.filter { it.taken || it.id !in active }.forEach {
            notifications.cancel(it.id)
            cancelFollowUp(context, it.id)
        }

        val next = if (store.settings.remindersEnabled && store.settings.onboardingDone) {
            nextReminder(remindable(store), LocalDateTime.now())
        } else {
            null
        }
        val alarm = alarmIntent(context, ACTION_DUE, NEXT_ALARM_REQUEST) {
            next?.let { putExtra(EXTRA_TIME, it.toLocalTime().toSecondOfDay()) }
        }
        if (next == null) {
            alarmManager.cancel(alarm)
        } else {
            setAlarm(context, next, alarm)
        }

        // Overdue doses keep being reminded, also when their due alarm never fired (e.g. the time
        // was set in the past, or the phone restarted). Pending follow-ups are left untouched.
        if (store.settings.remindersEnabled && store.settings.repeatReminders && store.settings.onboardingDone) {
            val now = LocalDateTime.now()
            remindable(store)
                .filter { !it.taken && !it.time!!.isAfter(now.toLocalTime()) && it.wasScheduledAt(now.toLocalDate().atTime(it.time)) }
                .filter { !hasFollowUp(context, it.id) }
                .forEach { scheduleFollowUp(context, it.id, repeatMinutes(store)) }
        }
    }

    private fun hasFollowUp(context: Context, medicationId: Int): Boolean =
        PendingIntent.getBroadcast(
            context,
            FOLLOW_UP_REQUEST_OFFSET + medicationId,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE_DUE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) != null

    /** Shows reminders for every untaken dose scheduled at [time]. */
    internal fun onDue(context: Context, time: LocalTime?) {
        val store = MedicationStore.get(context)
        if (time != null && store.settings.remindersEnabled) {
            remindable(store)
                .filter { it.time == time && !it.taken }
                .forEach { medication ->
                    notify(context, store, medication)
                    if (store.settings.repeatReminders) scheduleFollowUp(context, medication.id, repeatMinutes(store))
                }
        }
        sync(context)
    }

    /** "Später erinnern": hides the reminder and shows it again after the repeat interval. */
    internal fun snooze(context: Context, medicationId: Int) {
        NotificationManagerCompat.from(context).cancel(medicationId)
        scheduleFollowUp(context, medicationId, repeatMinutes(MedicationStore.get(context)))
    }

    /**
     * Follow-up for a dose that was snoozed or not confirmed: reminds again and, while repeating is on,
     * keeps going every interval until the dose is confirmed. Stops at the end of the day.
     */
    internal fun onFollowUpDue(context: Context, medicationId: Int, dueDay: LocalDate?) {
        val store = MedicationStore.get(context)
        val medication = remindable(store).firstOrNull { it.id == medicationId }
        val stillDue = (dueDay == null || dueDay == LocalDate.now()) &&
            medication != null && !medication.taken && store.settings.remindersEnabled
        if (!stillDue) {
            cancelFollowUp(context, medicationId)
            return
        }
        notify(context, store, medication!!)
        if (store.settings.repeatReminders) scheduleFollowUp(context, medicationId, repeatMinutes(store))
        else cancelFollowUp(context, medicationId)
    }

    /** Ends a follow-up chain; the PendingIntent itself is released so [hasFollowUp] sees it is gone. */
    private fun cancelFollowUp(context: Context, medicationId: Int) {
        val intent = followUpIntent(context, medicationId, LocalDate.now())
        context.getSystemService(AlarmManager::class.java).cancel(intent)
        intent.cancel()
    }

    private fun repeatMinutes(store: MedicationStore): Long =
        store.settings.repeatIntervalMinutes.toLong().takeIf { it > 0 } ?: DEFAULT_SNOOZE_MINUTES

    private fun scheduleFollowUp(context: Context, medicationId: Int, minutes: Long) {
        setAlarm(context, LocalDateTime.now().plusMinutes(minutes), followUpIntent(context, medicationId, LocalDate.now()))
    }

    private fun followUpIntent(context: Context, medicationId: Int, dueDay: LocalDate): PendingIntent =
        alarmIntent(context, ACTION_SNOOZE_DUE, FOLLOW_UP_REQUEST_OFFSET + medicationId) {
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_DUE_DAY, dueDay.toEpochDay())
        }

    private fun remindable(store: MedicationStore): List<Medication> {
        val careMode = store.settings.mode == AppMode.Care
        return store.activeMedications.filter { !it.asNeeded && (careMode || it.personId == Person.SELF_ID) }
    }

    /** The next intake time after [now]: a later untaken dose today, otherwise the first one tomorrow. */
    private fun nextReminder(medications: List<Medication>, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val laterToday = medications
            .filter { !it.taken && it.time!!.isAfter(now.toLocalTime()) }
            .minOfOrNull { it.time!! }
        if (laterToday != null) return today.atTime(laterToday)
        val first = medications.minOfOrNull { it.time!! } ?: return null
        return today.plusDays(1).atTime(first)
    }

    private fun setAlarm(context: Context, at: LocalDateTime, intent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Exact alarms need the user's permission from Android 12 on; otherwise Android may delay a little.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    private inline fun alarmIntent(
        context: Context,
        action: String,
        requestCode: Int,
        extras: Intent.() -> Unit
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, ReminderReceiver::class.java).setAction(action).apply(extras),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notify(context: Context, store: MedicationStore, medication: Medication) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return

        val careMode = store.settings.mode == AppMode.Care
        val person = store.person(medication.personId)?.takeIf { careMode }
        val open = PendingIntent.getActivity(
            context,
            medication.id,
            ReminderActivity.intent(context, medication.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            SNOOZE_ACTION_REQUEST_OFFSET + medication.id,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_MEDICATION_ID, medication.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (person != null) {
            "${person.firstName.ifBlank { person.fullName }}: ${medication.name}"
        } else {
            "Zeit für ${medication.name}"
        }
        val details = listOfNotNull(medication.dosage, medication.ingredient, medication.instructions)
            .joinToString("\n")

        val channel = if (store.settings.soundWhenMuted) ALARM_CHANNEL_ID else CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(listOfNotNull(medication.dosage, medication.instructions).joinToString(" – "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            // Stays until the intake is confirmed or snoozed.
            .setAutoCancel(false)
            .addAction(R.drawable.ic_check, "Jetzt bestätigen", open)
            .addAction(R.drawable.ic_schedule, "In ${repeatMinutes(store)} Min. erinnern", snooze)
        if (store.settings.lockScreenAlarm) {
            // On a locked phone this opens the confirmation screen right away, like an alarm clock.
            builder.setFullScreenIntent(open, true)
        }

        try {
            notifications.notify(medication.id, builder.build())
        } catch (_: SecurityException) {
            // Notification permission was revoked in the meantime.
        }
    }
}

/** Alarms and notification actions of the app itself. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getIntExtra(ReminderScheduler.EXTRA_MEDICATION_ID, -1)
        when (intent.action) {
            ReminderScheduler.ACTION_DUE -> ReminderScheduler.onDue(
                context,
                intent.getIntExtra(ReminderScheduler.EXTRA_TIME, -1).takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) }
            )
            ReminderScheduler.ACTION_SNOOZE -> if (medicationId >= 0) ReminderScheduler.snooze(context, medicationId)
            ReminderScheduler.ACTION_SNOOZE_DUE -> if (medicationId >= 0) {
                val dueDay = intent.getLongExtra(ReminderScheduler.EXTRA_DUE_DAY, Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE }
                    ?.let(LocalDate::ofEpochDay)
                ReminderScheduler.onFollowUpDue(context, medicationId, dueDay)
            }
        }
    }
}

/** Reschedules after events that clear or shift alarms: reboot, clock changes, app updates. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.sync(context)
    }
}
