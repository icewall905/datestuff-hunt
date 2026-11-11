package dk.datestuff.hunt

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/** azimuth: degrees from true north (0..360) of where the phone points; pitch: camera elevation (+up). */
data class Orientation(val azimuth: Double = 0.0, val pitch: Double = 0.0, val valid: Boolean = false)

object OrientationMath {
    /** Rotation matrix (row-major 9) -> azimuth/pitch of the direction the phone is pointing. */
    fun fromMatrix(r: FloatArray): Orientation {
        val flat = abs(r[8]) > 0.7f
        val e: Float; val n: Float; val up: Float
        if (flat) { e = r[1]; n = r[4]; up = r[7] }          // top edge of the phone
        else { e = -r[2]; n = -r[5]; up = -r[8] }            // back camera direction
        val az = Geo.norm360(Math.toDegrees(atan2(e.toDouble(), n.toDouble())))
        val pitch = Math.toDegrees(asin(up.toDouble().coerceIn(-1.0, 1.0)))
        return Orientation(az, if (flat) 0.0 else pitch, true)
    }

    /** Exponential smoothing on a circle. */
    fun smooth(prev: Double, next: Double, alpha: Double): Double = Geo.norm360(prev + Geo.angleDiff(prev, next) * alpha)
}

@Composable
fun rememberOrientation(): State<Orientation> {
    val ctx = LocalContext.current
    val st = remember { mutableStateOf(Orientation()) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val m = FloatArray(9)
        var decl = 0.0; var lastDeclAt = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(m, ev.values)
                val o = OrientationMath.fromMatrix(m)
                val now = System.currentTimeMillis()
                if (now - lastDeclAt > 60_000) {
                    lastDeclAt = now
                    HuntRepo.location.value?.let { decl = GeomagneticField(it.lat.toFloat(), it.lng.toFloat(), 0f, now).declination.toDouble() }
                }
                val prev = st.value
                val az = if (prev.valid) OrientationMath.smooth(prev.azimuth, Geo.norm360(o.azimuth + decl), 0.25) else Geo.norm360(o.azimuth + decl)
                st.value = Orientation(az, prev.pitch * 0.7 + o.pitch * 0.3, true)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    return st
}

/** Accelerometer (x, y, z) with gravity. */
@Composable
fun rememberAccel(onSample: (Float, Float, Float) -> Unit) {
    val ctx = LocalContext.current
    val cb = androidx.compose.runtime.rememberUpdatedState(onSample)
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) { cb.value(ev.values[0], ev.values[1], ev.values[2]) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
}
