package com.vonreyher.medicationtracker

import android.app.Application
import com.vonreyher.medicationtracker.reminders.ReminderScheduler

class MedicationApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        // Covers fresh installs and updates: make sure the next reminder is scheduled.
        ReminderScheduler.sync(this)
    }
}
