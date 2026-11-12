package dk.datestuff.hunt.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dk.datestuff.hunt.HuntState
import dk.datestuff.hunt.LatLng
import dk.datestuff.hunt.PhoneStepType
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** Default view until GPS or a pinned target is known (Copenhagen), instead of 0,0 in the Atlantic. */
private val FALLBACK = GeoPoint(55.676, 12.568)

/** Unpinned waypoints are stored as 0,0; they are not real places. */
internal fun pinned(lat: Double, lng: Double) = lat.isFinite() && lng.isFinite() && !(lat == 0.0 && lng == 0.0)

@Composable
fun MapTab(state: HuntState, loc: LatLng?) {
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<MapView>(1) }
    DisposableEffect(Unit) { onDispose { holder[0]?.onDetach(); holder[0] = null } }
    // clipToBounds: osmdroid's view otherwise paints tiles over the tabs and buttons around it
    AndroidView(modifier = Modifier.fillMaxSize().clipToBounds(), factory = {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = ctx.cacheDir.resolve("osm").apply { mkdirs() }
            osmdroidTileCache = ctx.cacheDir.resolve("osm/tiles").apply { mkdirs() }
        }
        MapView(it).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            clipToOutline = true
            controller.setZoom(15.0)
            controller.setCenter(FALLBACK)
            holder[0] = this
        }
    }, update = { map ->
        map.overlays.clear()
        val here = loc?.takeIf { pinned(it.lat, it.lng) }
        var center: GeoPoint? = here?.let { GeoPoint(it.lat, it.lng) }
        here?.let { l ->
            map.overlays.add(Marker(map).apply { position = GeoPoint(l.lat, l.lng); title = "YOU"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) })
        }
        for (s in state.openPhoneSteps) {
            val w = s.waypoint ?: continue
            if (!pinned(w.lat, w.lng)) continue
            // silent/geofence targets stay hidden on the map
            if (s.type == PhoneStepType.GEOFENCE || s.cfgBool("silent", false) || !s.cfgBool("revealDistance", true)) continue
            val p = GeoPoint(w.lat, w.lng)
            if (center == null) center = p
            map.overlays.add(Marker(map).apply { position = p; title = w.label.ifBlank { s.title } })
            map.overlays.add(Polygon(map).apply {
                points = Polygon.pointsAsCircle(p, w.radius)
                fillPaint.color = AColor.argb(50, 57, 255, 122); outlinePaint.color = AColor.rgb(57, 255, 122); outlinePaint.strokeWidth = 3f
            })
        }
        // centre once, on the first real position (GPS fix or target); never on 0,0
        if (map.tag == null) center?.let { map.controller.setZoom(17.0); map.controller.setCenter(it); map.tag = true }
        map.invalidate()
    })
}
