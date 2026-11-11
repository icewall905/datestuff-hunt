package dk.datestuff.hunt

import kotlin.math.abs

/**
 * Recognises tilt gestures from accelerometer samples (device axes, m/s^2).
 * left  = left edge down (x > t), right = right edge down (x < -t),
 * up    = top edge away, screen turns skyward (z > t), down = top edge towards you (z < -t).
 * After a gesture the phone must return near neutral (|x|,|z| < neutral) before the next one counts.
 */
class TiltDetector(
    private val sequence: List<String>,
    private val threshold: Float = 4.5f,
    private val neutral: Float = 2.5f,
) {
    var progress = 0; private set
    private var armed = false
    val done get() = sequence.isNotEmpty() && progress >= sequence.size

    /** Returns a gesture name when one is registered by this sample. */
    fun feed(x: Float, z: Float): String? {
        if (done) return null
        val g = when {
            x > threshold -> "left"
            x < -threshold -> "right"
            z > threshold -> "up"
            z < -threshold -> "down"
            else -> null
        }
        if (g == null) {
            if (abs(x) < neutral && abs(z) < neutral) armed = true
            return null
        }
        if (!armed) return null
        armed = false
        if (g == sequence[progress]) progress++ else progress = 0
        return g
    }

    fun reset() { progress = 0; armed = false }
}
