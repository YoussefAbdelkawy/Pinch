package dev.pinch.app.widget

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The tick under a tap on the widget's refresh arrow. A widget cannot vibrate by itself, so the
 * tap's receiver does it, as the arrow starts its turn.
 *
 * The receiver runs in the background, and Android drops a background app's "touch" vibrations.
 * Notification vibrations are let through, so the tick goes out as one: it follows the phone's
 * notification vibration setting and stays quiet when the phone is on silent.
 */
object Haptics {

    fun tick(context: Context) {
        try {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
        } catch (_: RuntimeException) {
            // A buzz is a nicety: never let it get in the way of the refresh.
        }
    }

    private const val TICK_MS = 12L

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
}
