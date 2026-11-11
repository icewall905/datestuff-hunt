package dk.datestuff.hunt.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dk.datestuff.hunt.Geo
import dk.datestuff.hunt.LatLng
import dk.datestuff.hunt.Orientation
import dk.datestuff.hunt.Waypoint
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

fun heatText(h: Geo.Heat) = when (h) {
    Geo.Heat.ICE -> "FREEZING"
    Geo.Heat.COLD -> "COLD"
    Geo.Heat.COOL -> "COOL"
    Geo.Heat.WARM -> "WARM"
    Geo.Heat.HOT -> "HOT"
    Geo.Heat.BURNING -> "ON TARGET"
}

fun heatColor(h: Geo.Heat): Color = when (h) {
    Geo.Heat.ICE, Geo.Heat.COLD -> Color(0xFF4AB8FF)
    Geo.Heat.COOL -> Phosphor
    Geo.Heat.WARM -> Amber
    Geo.Heat.HOT -> Color(0xFFFF8A3D)
    Geo.Heat.BURNING -> Danger
}

/**
 * Radar scope: rotating sweep, rings, target blip positioned by bearing-vs-heading and
 * log-scaled distance, plus a compass arrow to the target. [silent] hides blip and arrow.
 */
@Composable
fun Radar(wp: Waypoint, loc: LatLng?, orientation: State<Orientation>, silent: Boolean = false, showDistance: Boolean = true, showArrow: Boolean = true) {
    val dist = loc?.let { Geo.distance(it.lat, it.lng, wp.lat, wp.lng) }
    val bearing = loc?.let { Geo.bearing(it.lat, it.lng, wp.lat, wp.lng) }
    val heat = dist?.let { Geo.heat(it, wp.radius) }
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val o = orientation.value
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = min(size.width, size.height) / 2 - 6.dp.toPx()
            for (i in 1..4) drawCircle(Line, r * i / 4, c, style = Stroke(1.dp.toPx()))
            drawLine(Line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(Line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            // sweep wedge
            rotate(sweep, c) {
                val p = Path().apply { moveTo(c.x, c.y); lineTo(c.x, c.y - r) }
                drawPath(p, Phosphor.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
                for (k in 1..12) {
                    rotate(-k * 2.5f, c) { drawLine(Phosphor.copy(alpha = 0.22f - k * 0.017f), c, Offset(c.x, c.y - r), 3.dp.toPx()) }
                }
            }
            // north tick relative to heading
            rotate((-o.azimuth).toFloat(), c) {
                drawLine(Amber, Offset(c.x, c.y - r), Offset(c.x, c.y - r + 12.dp.toPx()), 3.dp.toPx())
            }
            if (!silent && bearing != null && dist != null) {
                val rel = Math.toRadians(Geo.angleDiff(o.azimuth, bearing))
                // log scale: 5 m -> 0.15R, 1 km -> ~R
                val f = (ln(1 + dist / 5.0) / ln(1 + 1000.0 / 5.0)).coerceIn(0.05, 1.0)
                val b = Offset((c.x + sin(rel) * r * f).toFloat(), (c.y - cos(rel) * r * f).toFloat())
                val col = heatColor(heat!!)
                drawCircle(col.copy(alpha = 0.25f * pulse), 16.dp.toPx() * (1.2f - pulse * 0.2f), b)
                drawCircle(col, 6.dp.toPx(), b)
                if (showArrow) rotate(Math.toDegrees(rel).toFloat(), c) {
                    val len = r * 0.55f
                    val tip = Offset(c.x, c.y - len)
                    val p = Path().apply {
                        moveTo(tip.x, tip.y); lineTo(tip.x - 12.dp.toPx(), tip.y + 22.dp.toPx()); lineTo(tip.x + 12.dp.toPx(), tip.y + 22.dp.toPx()); close()
                    }
                    drawLine(Amber, c, Offset(c.x, tip.y + 18.dp.toPx()), 3.dp.toPx())
                    drawPath(p, Amber)
                }
            }
            drawCircle(Phosphor, 4.dp.toPx(), c)
        }
        if (loc == null) Text("ACQUIRING GPS...", color = Amber, style = MaterialTheme.typography.titleLarge)
        else if (heat != null) {
            Text(heatText(heat), color = heatColor(heat), style = MaterialTheme.typography.headlineMedium)
            if (!silent && showDistance && dist != null) Text(Geo.formatDistance(dist), color = Phosphor, style = MaterialTheme.typography.displaySmall)
            if (loc.acc > 0) Text("GPS ±${loc.acc.toInt()} m", color = PhosphorDim, style = MaterialTheme.typography.bodySmall)
        }
        if (wp.label.isNotBlank() && !silent) Text(wp.label.uppercase(), color = PhosphorDim, style = MaterialTheme.typography.bodyMedium)
    }
}
