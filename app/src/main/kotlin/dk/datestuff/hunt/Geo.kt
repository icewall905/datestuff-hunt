package dk.datestuff.hunt

import kotlin.math.*

object Geo {
    private const val R = 6371000.0

    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * R * asin(min(1.0, sqrt(a)))
    }

    /** Initial bearing 0..360 from point 1 to point 2. */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return norm360(Math.toDegrees(atan2(y, x)))
    }

    fun norm360(d: Double): Double = ((d % 360) + 360) % 360

    /** Signed smallest difference to-from in -180..180. */
    fun angleDiff(from: Double, to: Double): Double {
        val d = norm360(to - from)
        return if (d > 180) d - 360 else d
    }

    enum class Heat { ICE, COLD, COOL, WARM, HOT, BURNING }

    fun heat(distance: Double, radius: Double): Heat = when {
        distance <= radius -> Heat.BURNING
        distance <= radius * 2 + 10 -> Heat.HOT
        distance <= radius * 4 + 40 -> Heat.WARM
        distance <= 250 -> Heat.COOL
        distance <= 800 -> Heat.COLD
        else -> Heat.ICE
    }

    fun formatDistance(m: Double): String = if (m < 1000) "${m.roundToInt()} m" else "%.1f km".format(m / 1000)
}
