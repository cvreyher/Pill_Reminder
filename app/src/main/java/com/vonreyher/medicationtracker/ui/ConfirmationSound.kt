package com.vonreyher.medicationtracker.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.vonreyher.medicationtracker.R

/**
 * Short chime played when an intake is confirmed. It plays on the media stream so it is audible
 * with the usual volume, and stays silent only when the phone is fully muted.
 */
class ConfirmationSound private constructor(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    @Volatile
    private var loaded = false
    @Volatile
    private var playWhenLoaded = false

    // The pool only holds this one sound, so any successful load means it is ready.
    private val soundId: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loaded = true
                if (playWhenLoaded) play()
            }
        }
        soundId = pool.load(context, R.raw.intake_confirmed, 1)
    }

    fun play() {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (!loaded) {
            playWhenLoaded = true
            return
        }
        playWhenLoaded = false
        pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    companion object {
        @Volatile
        private var instance: ConfirmationSound? = null

        /** Loaded once per process so the chime is ready the moment it is needed. */
        fun get(context: Context): ConfirmationSound =
            instance ?: synchronized(this) {
                instance ?: ConfirmationSound(context.applicationContext).also { instance = it }
            }
    }
}
