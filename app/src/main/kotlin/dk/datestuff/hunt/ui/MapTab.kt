package dk.datestuff.hunt.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun MapTab(state: HuntState, loc: LatLng?) {
    val ctx = LocalContext.current
    AndroidView(modifier = Modifier.fillMaxSize(), factory = {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = ctx.cacheDir.resolve("osm").apply { mkdirs() }
            osmdroidTileCache = ctx.cacheDir.resolve("osm/tiles").apply { mkdirs() }
        }
        MapView(it).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
        }
    }, update = { map ->
        map.overlays.clear()
        var center: GeoPoint? = loc?.let { GeoPoint(it.lat, it.lng) }
        loc?.let { l ->
            map.overlays.add(Marker(map).apply { position = GeoPoint(l.lat, l.lng); title = "YOU"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) })
        }
        for (s in state.openPhoneSteps) {
            val w = s.waypoint ?: continue
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
        if (map.tag == null) { center?.let { map.controller.setCenter(it); map.tag = true } }
        map.invalidate()
    })
}
