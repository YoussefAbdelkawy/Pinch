package dev.pinch.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** The short tap Pinch gives when a refresh is asked for, in the app and on the widget. */
object Haptics {

    /**
     * View haptics stay silent while the system's touch feedback is off (the default on many
     * phones), and since Android 12 so does a short vibration with no usage: the system files it
     * under touch feedback too. A widget tap is handled while the app is in the background, where
     * Android drops every vibration except a few kinds, and media is not one. Hardware feedback
     * is one of those kinds and does not follow the touch feedback switch, so it works from both
     * places. Before Android 13 there is no such usage; a notification one is the allowed kind.
     */
    fun tick(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() } ?: return
        val effect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
        } catch (_: Exception) {
        }
    }
}
