package dk.datestuff.hunt

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Haptics + tones for satisfying feedback. */
object Feedback {
    private var tone: ToneGenerator? = null
    private fun tg(): ToneGenerator? {
        if (tone == null) tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
        return tone
    }

    private fun vib(c: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun pattern(c: Context, p: LongArray) {
        runCatching { vib(c)?.vibrate(VibrationEffect.createWaveform(p, -1)) }
    }

    fun tick(c: Context) { pattern(c, longArrayOf(0, 12)); tg()?.startTone(ToneGenerator.TONE_PROP_BEEP, 40) }
    fun success(c: Context) { pattern(c, longArrayOf(0, 40, 60, 40, 60, 120)); tg()?.startTone(ToneGenerator.TONE_PROP_ACK, 250) }
    fun error(c: Context) { pattern(c, longArrayOf(0, 200)); tg()?.startTone(ToneGenerator.TONE_PROP_NACK, 200) }
    fun approach(c: Context) { pattern(c, longArrayOf(0, 80, 80, 80)); tg()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 150) }
    fun arrive(c: Context) { pattern(c, longArrayOf(0, 120, 80, 120, 80, 400)); tg()?.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 400) }
    fun message(c: Context) { pattern(c, longArrayOf(0, 60, 40, 60)); tg()?.startTone(ToneGenerator.TONE_SUP_PIP, 120) }
    fun collect(c: Context) { pattern(c, longArrayOf(0, 25)); tg()?.startTone(ToneGenerator.TONE_DTMF_9, 80) }
}
