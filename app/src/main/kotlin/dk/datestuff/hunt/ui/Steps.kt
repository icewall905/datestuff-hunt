package dk.datestuff.hunt.ui

import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.datestuff.hunt.AnswerOutcome
import dk.datestuff.hunt.Feedback
import dk.datestuff.hunt.Game
import dk.datestuff.hunt.Geo
import dk.datestuff.hunt.HuntRepo
import dk.datestuff.hunt.LatLng
import dk.datestuff.hunt.Orientation
import dk.datestuff.hunt.Step
import dk.datestuff.hunt.TiltDetector
import dk.datestuff.hunt.rememberAccel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun GpsStep(step: Step, loc: LatLng?, orientation: State<Orientation>, silent: Boolean) {
    val wp = step.waypoint
    if (wp == null) { Text("NO TARGET DEFINED.", color = Danger); return }
    val silentCfg = silent || step.cfgBool("silent", false)
    Radar(wp, loc, orientation, silent = silentCfg, showDistance = step.cfgBool("revealDistance", true), showArrow = step.cfgBool("arrow", true))
    val inside = loc != null && Geo.distance(loc.lat, loc.lng, wp.lat, wp.lng) <= wp.radius
    LaunchedEffect(inside) { if (inside) HuntRepo.solve(step.id) }
}

@Composable
fun SprintStep(step: Step, loc: LatLng?, orientation: State<Orientation>) {
    val wp = step.waypoint
    val total = step.cfgInt("seconds", 300)
    var endAt by remember { mutableLongStateOf(0L) }
    var left by remember { mutableIntStateOf(total) }
    var failed by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val running = endAt > 0 && !failed
    LaunchedEffect(endAt, failed) {
        while (endAt > 0 && !failed) {
            left = ((endAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            if (left <= 0) { failed = true; Feedback.error(ctx) }
            delay(250)
        }
    }
    if (wp == null) { Text("NO TARGET DEFINED.", color = Danger); return }
    val inside = loc != null && Geo.distance(loc.lat, loc.lng, wp.lat, wp.lng) <= wp.radius
    if (running) LaunchedEffect(inside) { if (inside) HuntRepo.solve(step.id) }
    Text(fmtClock(left * 1000L), color = if (left < 30 && running) Danger else Amber, style = MaterialTheme.typography.displayLarge)
    Radar(wp, loc, orientation, showDistance = step.cfgBool("revealDistance", true))
    if (!running) {
        if (failed) Text("TOO SLOW. NO PENALTY. RETRY.", color = Amber, style = MaterialTheme.typography.titleMedium)
        HudSolidButton(if (failed) "Retry" else "Start sprint", Modifier.fillMaxWidth()) {
            failed = false; left = total; endAt = System.currentTimeMillis() + total * 1000L
        }
    }
}

@Composable
fun QrStep(step: Step) {
    val code = step.cfgString("code")
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var lastBad by remember { mutableLongStateOf(0L) }
    RequirePermission(CAMERA, "CAMERA") {
        Box(Modifier.fillMaxSize()) {
            QrScanner(Modifier.fillMaxSize()) { raw ->
                val ok = raw.trim() == code.trim() || (Game.norm(code).isNotEmpty() && Game.norm(raw) == Game.norm(code))
                if (ok) HuntRepo.solve(step.id)
                else if (System.currentTimeMillis() - lastBad > 1500) { lastBad = System.currentTimeMillis(); Feedback.error(ctx); msg = "WRONG CODE" }
            }
            Canvas(Modifier.align(Alignment.Center).size(240.dp)) {
                drawRect(Phosphor, style = Stroke(3.dp.toPx()), topLeft = Offset.Zero, size = Size(size.width, size.height))
            }
            Text(msg.ifEmpty { "SCAN THE CODE" }, color = if (msg.isEmpty()) Phosphor else Danger, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
            Text(step.text.firstOrNull() ?: "", color = Amber, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
        }
    }
}

@Composable
fun PhotoStep(step: Step, front: Boolean) {
    val ctx = LocalContext.current
    val state by HuntRepo.state.collectAsStateWithLifecycle()
    var phase by remember { mutableStateOf("capture") } // capture | review | waiting
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val status = state.session?.photos?.lastOrNull { it.step == step.id }?.status
    Text(step.cfgString("prompt").uppercase(), color = Amber, style = MaterialTheme.typography.titleLarge)
    when (phase) {
        "capture" -> RequirePermission(CAMERA, "CAMERA") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CameraPreview(Modifier.fillMaxWidth().height(420.dp), front = front, capture = capture)
                HudSolidButton("Shutter", Modifier.fillMaxWidth()) {
                    takeJpeg(ctx, capture) { b -> if (b != null) { Feedback.tick(ctx); jpeg = b; phase = "review" } else Feedback.error(ctx) }
                }
            }
        }
        "review" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            jpeg?.let { b ->
                val bmp = remember(b) { BitmapFactory.decodeByteArray(b, 0, b.size).asImageBitmap() }
                Image(bmp, null, Modifier.fillMaxWidth().height(420.dp), contentScale = ContentScale.Fit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("Retake", Modifier.weight(1f)) { phase = "capture" }
                HudSolidButton("Send", Modifier.weight(1f)) { jpeg?.let { HuntRepo.submitPhoto(step.id, it) }; phase = "waiting" }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == "rejected") {
                Text("REJECTED BY MISSION CONTROL. RETAKE.", color = Danger, style = MaterialTheme.typography.titleMedium)
                HudButton("Retake", Modifier.fillMaxWidth()) { phase = "capture" }
            } else {
                Text(if (state.pending > 0) "UPLOADING (QUEUED UNTIL ONLINE)..." else "AWAITING MISSION CONTROL APPROVAL...", color = Amber, style = MaterialTheme.typography.titleMedium)
                PulseBar()
            }
        }
    }
}

@Composable
fun PulseBar() {
    val x by rememberInfiniteTransition(label = "pb").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRect(Line, size = size)
        drawRect(Phosphor, Offset(size.width * x * 0.8f, 0f), Size(size.width * 0.2f, size.height))
    }
}

@Composable
fun QuestionStep(step: Step) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun submit() {
        if (busy || text.isBlank()) return
        busy = true
        scope.launch {
            when (HuntRepo.answer(step, text)) {
                AnswerOutcome.Correct -> { msg = "" }
                AnswerOutcome.Wrong -> { Feedback.error(ctx); msg = "ACCESS DENIED" }
                AnswerOutcome.Locked -> { Feedback.error(ctx); msg = "NOT UNLOCKED YET" }
                AnswerOutcome.Error -> { Feedback.error(ctx); msg = "TRANSMISSION ERROR" }
            }
            busy = false
        }
    }
    step.cfgString("question").takeIf { it.isNotBlank() }?.let { Text(it.uppercase(), color = Amber, style = MaterialTheme.typography.titleLarge) }
    OutlinedTextField(text, { text = it; msg = "" }, Modifier.fillMaxWidth(), label = { Text("ANSWER") }, singleLine = true, colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
    if (msg.isNotEmpty()) Text(msg, color = Danger, style = MaterialTheme.typography.titleMedium)
    HudSolidButton("Transmit", Modifier.fillMaxWidth(), enabled = !busy && text.isNotBlank()) { submit() }
}

@Composable
fun CompassStep(step: Step, orientation: State<Orientation>) {
    val target = step.cfgDouble("bearing", 0.0)
    val tol = step.cfgDouble("tolerance", 10.0)
    val hold = step.cfgDouble("holdSeconds", 3.0).coerceAtLeast(0.5)
    val o = orientation.value
    val off = abs(Geo.angleDiff(o.azimuth, target))
    val inTol = o.valid && off <= tol
    var progress by remember { mutableFloatStateOf(0f) }
    val inTolState = androidx.compose.runtime.rememberUpdatedState(inTol)
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            delay(50)
            val now = System.nanoTime(); val dt = (now - last) / 1e9f; last = now
            val prev = progress
            progress = if (inTolState.value) min(1f, progress + dt / hold.toFloat()) else (progress - dt * 1.5f / hold.toFloat()).coerceAtLeast(0f)
            if ((prev * 10).toInt() != (progress * 10).toInt() && progress > prev) Feedback.tick(ctx)
            if (progress >= 1f) { HuntRepo.solve(step.id); break }
        }
    }
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(280.dp), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 14.dp.toPx()
                drawArc(Line, 0f, 360f, false, Offset(s, s), Size(size.width - 2 * s, size.height - 2 * s), style = Stroke(s))
                drawArc(if (inTol) Phosphor else Amber, -90f, 360f * progress, false, Offset(s, s), Size(size.width - 2 * s, size.height - 2 * s), style = Stroke(s))
            }
            Text("${o.azimuth.toInt()}°", color = if (inTol) Phosphor else Amber, style = MaterialTheme.typography.displayMedium)
        }
        Text(if (inTol) "HOLD STEADY..." else if (off < tol * 3) "CLOSE" else "FIND THE HEADING", color = if (inTol) Phosphor else Amber, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun TiltStep(step: Step) {
    val seq = remember(step.id) { step.cfgStrings("sequence") }
    val det = remember(step.id) { TiltDetector(seq) }
    val shake = step.cfgBool("shakeToReset", true)
    val ctx = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf("") }
    var shakeAt by remember { mutableLongStateOf(0L) }
    rememberAccel { x, y, z ->
        val g = det.feed(x, z)
        if (g != null) {
            last = g
            val before = progress
            progress = det.progress
            if (progress > before) Feedback.tick(ctx) else Feedback.error(ctx)
            if (det.done) HuntRepo.solve(step.id)
        }
        if (shake) {
            val mag = sqrt(x * x + y * y + z * z)
            val now = System.currentTimeMillis()
            if (mag > 24f && now - shakeAt > 1000) { shakeAt = now; det.reset(); progress = 0; last = "RESET"; Feedback.error(ctx) }
        }
    }
    Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Alignment.CenterHorizontally) {
        Text("TILT THE DEVICE TO ENTER THE SEQUENCE", color = Amber, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            seq.forEachIndexed { i, _ ->
                Box(Modifier.size(36.dp), Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawRect(if (i < progress) Phosphor else Line, style = if (i < progress) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.dp.toPx())) }
                }
            }
        }
        Text(last.uppercase(), color = Phosphor, style = MaterialTheme.typography.displaySmall)
        if (shake) Text("SHAKE TO RESET", color = PhosphorDim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun WalkieStep() {
    val a by rememberInfiniteTransition(label = "w").animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
        Text("CONTACT MISSION CONTROL", color = Phosphor.copy(alpha = a), style = MaterialTheme.typography.headlineLarge)
        Text("Use your radio / voice channel. Awaiting clearance.", color = PhosphorDim, style = MaterialTheme.typography.bodyLarge)
        PulseBar()
    }
}
