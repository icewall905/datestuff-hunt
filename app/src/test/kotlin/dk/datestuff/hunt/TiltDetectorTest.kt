package dk.datestuff.hunt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltDetectorTest {
    private val g = 9.81f
    /** Gravity reading for a phone pitched back by [pitchDeg] (0 = flat, 90 = upright) and rolled by [rollDeg] (+ = left edge down). */
    private fun sample(pitchDeg: Double, rollDeg: Double = 0.0): FloatArray {
        val p = Math.toRadians(pitchDeg); val r = Math.toRadians(rollDeg)
        val x = (g * sin(r)).toFloat(); val yz = g * cos(r)
        return floatArrayOf(x, (yz * sin(p)).toFloat(), (yz * cos(p)).toFloat())
    }
    private fun TiltDetector.at(pitch: Double, roll: Double = 0.0, n: Int = 3): String? {
        var last: String? = null
        repeat(n) { val s = sample(pitch, roll); feed(s[0], s[1], s[2])?.let { last = it } }
        return last
    }

    @Test fun worksWhenHeldAtANormalReadingAngle() {
        val d = TiltDetector(listOf("left", "up", "right", "down"))
        d.at(50.0, n = 10) // calibrate: phone angled back like when reading
        assertEquals("left", d.at(50.0, 35.0)); d.at(50.0)
        assertEquals("up", d.at(15.0)); d.at(50.0)
        assertEquals("right", d.at(50.0, -35.0)); d.at(50.0)
        assertEquals("down", d.at(85.0))
        assertTrue(d.done)
    }

    @Test fun worksUprightAndFlat() {
        for (hold in listOf(88.0, 5.0)) {
            val d = TiltDetector(listOf("right", "left"))
            d.at(hold, n = 10)
            assertEquals("right", d.at(hold, -40.0)); d.at(hold)
            assertEquals("left", d.at(hold, 40.0))
            assertTrue("hold $hold", d.done)
        }
    }

    @Test fun mustReturnToNeutralBetweenMoves() {
        val d = TiltDetector(listOf("left", "left"))
        d.at(50.0, n = 10)
        assertEquals("left", d.at(50.0, 35.0))
        assertNull(d.at(50.0, 40.0)) // still tilted: not a second gesture
        assertEquals(1, d.progress)
        d.at(50.0); assertEquals("left", d.at(50.0, 35.0))
        assertTrue(d.done)
    }

    @Test fun smallWobblesAndShakesAreIgnored() {
        val d = TiltDetector(listOf("left"))
        d.at(50.0, n = 10)
        assertNull(d.at(60.0, 10.0))
        assertNull(d.feed(30f, 5f, 5f)) // shake: |g| far from 9.8
        assertFalse(d.done)
    }

    @Test fun wrongMoveRestartsSequenceAndResetRecalibrates() {
        val d = TiltDetector(listOf("left", "right"))
        d.at(50.0, n = 10)
        d.at(50.0, 35.0); d.at(50.0)
        assertEquals("up", d.at(15.0))
        assertEquals(0, d.progress)
        d.reset(); assertFalse(d.calibrated)
        d.at(80.0, n = 10); assertTrue(d.calibrated)
        assertEquals("left", d.at(80.0, 35.0))
    }
}
