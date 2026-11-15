package com.vonreyher.medicationtracker.reminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.vonreyher.medicationtracker.data.AppMode
import com.vonreyher.medicationtracker.data.MedicationStore
import com.vonreyher.medicationtracker.ui.ConfirmationSound
import com.vonreyher.medicationtracker.ui.MedicationDetailScreen
import com.vonreyher.medicationtracker.ui.rememberCurrentTime
import com.vonreyher.medicationtracker.ui.theme.MedicationTrackerTheme

/**
 * Opened from a reminder: shows only the confirmation screen of one dose, also on top of the lock
 * screen, without exposing the rest of the app.
 */
class ReminderActivity : ComponentActivity() {

    private val medicationId = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        medicationId.intValue = intent.getIntExtra(EXTRA_MEDICATION_ID, -1)

        val store = MedicationStore.get(this)
        ConfirmationSound.get(this)

        setContent {
            MedicationTrackerTheme {
                val now by rememberCurrentTime()
                val medication = store.medications.firstOrNull { it.id == medicationId.intValue }
                if (medication == null) {
                    finish()
                    return@MedicationTrackerTheme
                }
                MedicationDetailScreen(
                    medication = medication,
                    person = store.person(medication.personId).takeIf { store.settings.mode == AppMode.Care },
                    intakesToday = store.intakesToday(medication.id).size,
                    emaCredentials = store.settings.ema,
                    now = now,
                    onClose = ::finish,
                    onConfirmTaken = { store.confirmIntake(medication) },
                    onMarkNotTaken = { store.undoLatestIntakeToday(medication) },
                    onChangeTime = null
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        medicationId.intValue = intent.getIntExtra(EXTRA_MEDICATION_ID, medicationId.intValue)
    }

    companion object {
        private const val EXTRA_MEDICATION_ID = "medicationId"

        fun intent(context: Context, medicationId: Int): Intent =
            Intent(context, ReminderActivity::class.java)
                .putExtra(EXTRA_MEDICATION_ID, medicationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
