package io.embodit.perch.ui.focus.components

import android.media.AudioManager
import android.media.ToneGenerator

/** One soft system tone for "block ended" / "break ended"; never repeated, never looping. */
object Chime {
    fun play() {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
            Thread { Thread.sleep(400); generator.release() }.start()
        }
    }
}
