package dk.datestuff.hunt

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Recognises tilt gestures from accelerometer samples (device axes, m/s^2), relative to how the
 * player is holding the phone: the first steady samples set the neutral angle, so it works whether
 * the phone is held upright, angled back, or flat.
 *
 * left  = left edge down, right = right edge down (roll),
 * up    = top edge away from you, down = top edge towards you (pitch, relative to neutral).
 * After a gesture the phone must come back near neutral before the next one counts.
 */
class TiltDetector(
    private val sequence: List<String>,
    private val triggerDeg: Float = 25f,
    private val neutralDeg: Float = 12f,
) {
    var progress = 0; private set
    /** Current roll (+ = left edge down) and pitch relative to neutral (+ = top edge towards you), degrees. */
    var roll = 0f; private set
    var pitch = 0f; private set
    val done get() = sequence.isNotEmpty() && progress >= sequence.size
    val calibrated get() = base != null

    private var armed = false
    private var base: Float? = null
    private var calSum = 0f
    private var calN = 0

    /** Returns a gesture name when one is registered by this sample. */
    fun feed(x: Float, y: Float, z: Float): String? {
        val g = sqrt(x * x + y * y + z * z)
        if (g < 6f || g > 14f) return null // moving or shaking: angles are meaningless
        val r = Math.toDegrees(asin((x / g).coerceIn(-1f, 1f).toDouble())).toFloat()
        val p = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
        val b = base ?: run {
            calSum += p; calN++
            if (calN >= 8) { base = calSum / calN; armed = true }
            roll = r; pitch = 0f
            return null
        }
        roll = r; pitch = p - b
        if (done) return null
        val gesture = when {
            abs(roll) >= abs(pitch) && roll > triggerDeg -> "left"
            abs(roll) >= abs(pitch) && roll < -triggerDeg -> "right"
            pitch < -triggerDeg -> "up"
            pitch > triggerDeg -> "down"
            else -> null
        }
        if (gesture == null) {
            if (abs(roll) < neutralDeg && abs(pitch) < neutralDeg) {
                armed = true
                base = b + 0.02f * pitch // follow slow drift in how the phone is held
            }
            return null
        }
        if (!armed) return null
        armed = false
        if (gesture == sequence[progress]) progress++ else progress = if (gesture == sequence[0]) 1 else 0
        return gesture
    }

    fun reset() { progress = 0; armed = false; base = null; calSum = 0f; calN = 0 }
}
