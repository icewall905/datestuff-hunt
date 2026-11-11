package dk.datestuff.hunt.ui

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dk.datestuff.hunt.Feedback
import dk.datestuff.hunt.Game
import dk.datestuff.hunt.Geo
import dk.datestuff.hunt.HuntRepo
import dk.datestuff.hunt.LatLng
import dk.datestuff.hunt.Orientation
import dk.datestuff.hunt.Step
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode as ModelNodeImpl
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** null while checking, true when ARCore can run (or can be installed on demand). */
@Composable
fun rememberArAvailable(): Boolean? {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        repeat(20) {
            val a = runCatching { ArCoreApk.getInstance().checkAvailability(ctx) }.getOrNull()
            if (a == null) { ok = false; return@LaunchedEffect }
            if (a.isTransient) delay(200) else { ok = a.isSupported; return@LaunchedEffect }
        }
        ok = false
    }
    return ok
}

private fun Boolean?.arText() = when (this) { null -> "CHECKING AR SUPPORT..."; else -> "" }

// ---------------------------------------------------------------- beacon (camera overlay, no ARCore)

@Composable
fun ArBeaconStep(step: Step, loc: LatLng?, orientation: State<Orientation>) {
    val wp = step.waypoint
    if (wp == null) { Text("NO TARGET DEFINED.", color = Danger); return }
    val color = remember(step.id) { runCatching { Color(android.graphics.Color.parseColor(step.cfgString("color", "#39ff7a"))) }.getOrDefault(Phosphor) }
    val label = step.cfgString("label", "BEACON")
    val dist = loc?.let { Geo.distance(it.lat, it.lng, wp.lat, wp.lng) }
    val bearing = loc?.let { Geo.bearing(it.lat, it.lng, wp.lat, wp.lng) }
    val inside = dist != null && dist <= wp.radius
    LaunchedEffect(inside) { if (inside) HuntRepo.solve(step.id) }
    val pulse by rememberInfiniteTransition(label = "b").animateFloat(0.6f, 1.2f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    RequirePermission(CAMERA, "CAMERA") {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            CameraPreview(Modifier.fillMaxSize())
            val o = orientation.value
            val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
            val hfov = 60.0; val vfov = 75.0
            val dAz = if (bearing != null) Geo.angleDiff(o.azimuth, bearing) else 0.0
            val onScreen = bearing != null && abs(dAz) < hfov / 2
            Canvas(Modifier.fillMaxSize()) {
                if (bearing == null || dist == null) return@Canvas
                if (onScreen) {
                    val x = (wPx / 2 + (dAz / hfov * wPx)).toFloat()
                    // target on the horizon; camera pitched up moves the horizon down
                    val y = (hPx / 2 + (o.pitch / vfov * hPx)).toFloat().coerceIn(60f, hPx - 60f)
                    val r = 28.dp.toPx() * pulse
                    drawCircle(color.copy(alpha = 0.25f), r * 1.6f, Offset(x, y))
                    drawCircle(color, r, Offset(x, y), style = Stroke(3.dp.toPx()))
                    val p = Path().apply { moveTo(x, y + r * 2.4f); lineTo(x - 14.dp.toPx(), y + r * 1.6f); lineTo(x + 14.dp.toPx(), y + r * 1.6f); close() }
                    drawPath(p, color)
                } else {
                    // edge arrow pointing the way to turn
                    val dir = if (dAz > 0) 1f else -1f
                    val x = if (dir > 0) wPx - 40.dp.toPx() else 40.dp.toPx()
                    val y = hPx / 2
                    rotate(if (dir > 0) 90f else -90f, Offset(x, y)) {
                        val p = Path().apply { moveTo(x, y - 30.dp.toPx()); lineTo(x - 22.dp.toPx(), y + 12.dp.toPx()); lineTo(x + 22.dp.toPx(), y + 12.dp.toPx()); close() }
                        drawPath(p, color)
                    }
                }
            }
            Text(
                if (dist != null) "$label  ${Geo.formatDistance(dist)}" else "ACQUIRING GPS...",
                color = color, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Color(0x99000000)).padding(8.dp),
            )
            if (inside) Text("ARRIVED", color = Bg, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.Center).background(color).padding(12.dp))
        }
    }
}

// ---------------------------------------------------------------- spark particles (2D overlay)

private class Spark(val vx: Float, val vy: Float, val size: Float, val hue: Color)

@Composable
fun SparkBurst(trigger: Boolean, modifier: Modifier = Modifier) {
    val t = remember { Animatable(0f) }
    val sparks = remember { List(48) { Spark((Random.nextFloat() - 0.5f) * 900f, -Random.nextFloat() * 900f - 200f, 4f + Random.nextFloat() * 8f, if (Random.nextBoolean()) Amber else Phosphor) } }
    LaunchedEffect(trigger) { if (trigger) { t.snapTo(0f); t.animateTo(1f, tween(1600, easing = LinearEasing)) } }
    if (t.value in 0.001f..0.999f) Canvas(modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height * 0.55f)
        val tt = t.value
        for (s in sparks) {
            val x = c.x + s.vx * tt; val y = c.y + s.vy * tt + 1400f * tt * tt
            drawCircle(s.hue.copy(alpha = 1f - tt), s.size, Offset(x, y))
        }
    }
}

// ---------------------------------------------------------------- chest

@Composable
fun ArChestStep(step: Step) {
    val avail = rememberArAvailable()
    if (avail == null) { Text(avail.arText(), color = Amber, style = MaterialTheme.typography.titleMedium); return }
    var opened by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val clue = step.cfgString("clue", "")
    var burst by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val needsAnswer = Game.answersOf(step).isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        if (!opened) {
            if (avail) RequirePermission(CAMERA, "CAMERA") {
                ArChest(onOpened = { opened = true; burst = true; Feedback.success(ctx) })
            } else FallbackChest(onOpened = { opened = true; burst = true; Feedback.success(ctx) })
        }
        SparkBurst(burst)
        if (opened) Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            HudPanel(accent = Amber) {
                Text("THE CHEST OPENS...", color = Amber, style = MaterialTheme.typography.titleMedium)
                Typewriter(clue.ifBlank { "Empty. Someone got here first." }, color = Phosphor) { done = true }
            }
            if (done) {
                if (needsAnswer) QuestionStep(step)
                else HudSolidButton("Take it", Modifier.fillMaxWidth()) { HuntRepo.solve(step.id) }
            }
        }
    }
}

@Composable
private fun FallbackChest(onOpened: () -> Unit) {
    val wob by rememberInfiniteTransition(label = "c").animateFloat(-4f, 4f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "w")
    Column(Modifier.fillMaxSize().clickable { onOpened() }, Arrangement.spacedBy(12.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Canvas(Modifier.size(220.dp)) {
            rotate(wob, center) {
                drawRect(Color(0xFF6B3E19), Offset(size.width * 0.1f, size.height * 0.4f), androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.45f))
                drawRect(Color(0xFF4D2A10), Offset(size.width * 0.1f, size.height * 0.25f), androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.17f))
                drawRect(Amber, Offset(size.width * 0.45f, size.height * 0.38f), androidx.compose.ui.geometry.Size(size.width * 0.1f, size.height * 0.14f))
            }
        }
        Text("AR UNAVAILABLE. TAP TO OPEN.", color = Amber, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ArChest(onOpened: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val chest = rememberModelInstance(modelLoader, "models/chest.glb")
    val ctx = LocalContext.current
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var node by remember { mutableStateOf<ModelNodeImpl?>(null) }
    var tracking by remember { mutableStateOf(false) }
    var arFrame by remember { mutableStateOf<com.google.ar.core.Frame?>(null) }
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine, modelLoader = modelLoader,
            sessionConfiguration = { _, cfg ->
                cfg.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                cfg.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onSessionUpdated = { _, frame -> arFrame = frame; tracking = frame.camera.trackingState == TrackingState.TRACKING },
            onTouchEvent = { e, _ ->
                val hit = arFrame?.hitTest(e)?.firstOrNull { (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true }
                if (e.actionMasked == MotionEvent.ACTION_UP && anchor == null && hit != null && (hit.trackable as? Plane)?.isPoseInPolygon(hit.hitPose) == true) {
                    anchor = hit.createAnchor(); Feedback.tick(ctx); true
                } else false
            },
        ) {
            anchor?.let { a ->
                AnchorNode(a) {
                    if (chest != null) ModelNode(
                        modelInstance = chest, autoAnimate = false, scaleToUnits = 0.6f, centerOrigin = Position(0f, -1f, 0f),
                        apply = {
                            node = this
                            isTouchable = true
                            onSingleTapConfirmed = {
                                if (!opening) {
                                    opening = true
                                    playAnimation("open", 1f, false)
                                    scope.launch { delay(1100); onOpened() }
                                }
                                true
                            }
                        },
                    )
                }
            }
        }
        Text(
            when { anchor != null && !opening -> "TAP THE CHEST"; anchor != null -> "..."; tracking -> "TAP A FLAT SURFACE TO PLACE THE CHEST"; else -> "SCAN THE FLOOR SLOWLY..." },
            color = Phosphor, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Color(0x99000000)).padding(8.dp),
        )
    }
}

// ---------------------------------------------------------------- collect

@Composable
fun ArCollectStep(step: Step) {
    val avail = rememberArAvailable()
    if (avail == null) { Text(avail.arText(), color = Amber, style = MaterialTheme.typography.titleMedium); return }
    val count = step.cfgInt("count", 5).coerceIn(1, 30)
    val spread = step.cfgDouble("spread", 6.0).coerceIn(1.5, 40.0)
    var collected by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val onCollect = {
        collected++; Feedback.collect(ctx)
        if (collected >= count) HuntRepo.solve(step.id)
    }
    Box(Modifier.fillMaxSize()) {
        if (avail) RequirePermission(CAMERA, "CAMERA") { ArCollect(count, spread, collected, onCollect) }
        else FallbackCollect(count, collected, onCollect)
        Text("COLLECTED $collected / $count", color = Amber, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color(0x99000000)).padding(8.dp))
    }
}

@Composable
private fun ArCollect(count: Int, spread: Double, collected: Int, onCollect: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    var session by remember { mutableStateOf<Session?>(null) }
    val anchors = remember { mutableStateListOf<Anchor>() }
    var spawned by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }
    val cb = androidx.compose.runtime.rememberUpdatedState(onCollect)

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(), engine = engine, modelLoader = modelLoader,
            sessionConfiguration = { _, cfg -> cfg.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL },
            onSessionCreated = { session = it },
            onSessionUpdated = { s, frame ->
                tracking = frame.camera.trackingState == TrackingState.TRACKING
                if (!spawned && tracking) {
                    val plane = s.getAllTrackables(Plane::class.java).firstOrNull {
                        it.trackingState == TrackingState.TRACKING && it.subsumedBy == null && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                    }
                    if (plane != null) {
                        spawned = true
                        val camPose = frame.camera.pose
                        for (i in 0 until count) {
                            val ang = Random.nextDouble() * 2 * Math.PI
                            val r = 1.2 + Random.nextDouble() * (spread - 1.2).coerceAtLeast(0.5)
                            val local = Pose.makeTranslation((sin(ang) * r).toFloat(), 0f, (cos(ang) * r).toFloat())
                            // plane pose has Y up; offsets are in the plane's frame
                            runCatching { anchors.add(s.createAnchor(plane.centerPose.compose(local))) }
                        }
                        @Suppress("UNUSED_VARIABLE") val unused = camPose
                    }
                }
            },
        ) {
            for (a in anchors.toList()) {
                androidx.compose.runtime.key(a) {
                val feather = rememberModelInstance(modelLoader, "models/feather.glb")
                AnchorNode(a) {
                    if (feather != null) ModelNode(
                        modelInstance = feather, autoAnimate = true, scaleToUnits = 0.35f, centerOrigin = Position(0f, -1f, 0f),
                        apply = {
                            isTouchable = true
                            onFrame = { t -> rotation = io.github.sceneview.math.Rotation(y = ((t / 8_000_000L) % 360).toFloat()) }
                            onSingleTapConfirmed = {
                                if (anchors.remove(a)) { runCatching { a.detach() }; cb.value() }
                                true
                            }
                        },
                    )
                }
                }
            }
        }
        Text(if (!tracking) "SCAN THE AREA SLOWLY..." else if (!spawned) "LOOKING FOR GROUND..." else "FIND AND TAP THE FEATHERS",
            color = Phosphor, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).background(Color(0x99000000)).padding(8.dp))
    }
}

@Composable
private fun FallbackCollect(count: Int, collected: Int, onCollect: () -> Unit) {
    val pos = remember { List(count) { Offset(Random.nextFloat(), Random.nextFloat() * 0.8f + 0.05f) } }
    val gone = remember { mutableStateListOf<Int>() }
    val bob by rememberInfiniteTransition(label = "f").animateFloat(-8f, 8f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "y")
    CameraPreviewIfAllowed()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        pos.forEachIndexed { i, p ->
            if (i !in gone) Text("◆", color = Phosphor, fontSize = 44.sp, modifier = Modifier
                .offset { IntOffset((p.x * (w - 120)).roundToInt(), (p.y * h + bob).roundToInt()) }
                .clickable { gone.add(i); onCollect() }.padding(12.dp))
        }
        Text("AR UNAVAILABLE. TAP THE TARGETS.", color = Amber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
    }
}

@Composable
private fun CameraPreviewIfAllowed() {
    val ctx = LocalContext.current
    if (ctx.has(CAMERA)) CameraPreview(Modifier.fillMaxSize())
}
