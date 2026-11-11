package dk.datestuff.hunt.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.datestuff.hunt.Feedback
import dk.datestuff.hunt.Geo
import dk.datestuff.hunt.HuntRepo
import dk.datestuff.hunt.PhoneStepType
import dk.datestuff.hunt.Step
import dk.datestuff.hunt.rememberOrientation
import kotlinx.coroutines.launch

private val SELF_LOCATED = setOf(PhoneStepType.GPS, PhoneStepType.GEOFENCE, PhoneStepType.SPRINT, PhoneStepType.AR_BEACON)

@Composable
fun StepHost(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by HuntRepo.state.collectAsStateWithLifecycle()
    val loc by HuntRepo.location.collectAsStateWithLifecycle()
    val step = state.step(id)
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf<String?>(null) }
    if (step == null) { LaunchedEffect(Unit) { onBack() }; return }
    val solved = id in state.solvedIds
    val orientation = rememberOrientation()

    val wp = step.waypoint
    val dist = if (wp != null && loc != null) Geo.distance(loc!!.lat, loc!!.lng, wp.lat, wp.lng) else null
    var arrived by rememberSaveable(id) { mutableStateOf(false) }
    if (dist != null && wp != null && dist <= wp.radius) arrived = true
    val gated = wp != null && step.type !in SELF_LOCATED && (step.type != PhoneStepType.AR_CHEST || step.cfgBool("requireWaypoint", true))

    val fullBleed = !solved && (!gated || arrived) && step.type in setOf(PhoneStepType.AR_CHEST, PhoneStepType.AR_COLLECT, PhoneStepType.AR_BEACON, PhoneStepType.QR)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("<", onClick = onBack)
            Text(step.title.ifBlank { step.id }, color = Phosphor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2)
            Tag(step.type, Amber)
        }
        if (!fullBleed) Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Lines(step.text)
            if (solved) Complete(step)
            else if (gated && !arrived) {
                SectionTitle("Proceed to target")
                if (wp != null) Radar(wp, loc, orientation, showDistance = step.cfgBool("revealDistance", true), showArrow = step.cfgBool("arrow", true))
            } else StepBody(step, loc, orientation)
        } else Column(Modifier.weight(1f)) { StepBody(step, loc, orientation) }

        if (!solved) {
            hint?.let { HudPanel(accent = Amber) { Text(it, color = Amber, style = MaterialTheme.typography.bodyLarge) } }
            HudButton("Request hint", Modifier.fillMaxWidth(), amber = true) {
                scope.launch { hint = HuntRepo.requestHint(step.id) ?: "NO HINT AVAILABLE." }
            }
        }
    }
    LaunchedEffect(solved) { if (solved) Feedback.success(ctx) }
}

@Composable
fun Complete(step: Step) {
    val state by HuntRepo.state.collectAsStateWithLifecycle()
    val scale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val frag = state.fragments()[step.id]
    HudPanel(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, Phosphor) {
        Text("STEP COMPLETE", color = Phosphor, style = MaterialTheme.typography.headlineMedium)
        if (!frag.isNullOrEmpty()) {
            Text("FRAGMENT ACQUIRED", color = Amber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(frag, color = Amber, style = MaterialTheme.typography.displayLarge)
        }
        step.reward?.message?.takeIf { it.isNotBlank() }?.let { Typewriter(it) }
    }
}

@Composable
fun StepBody(step: Step, loc: dk.datestuff.hunt.LatLng?, orientation: androidx.compose.runtime.State<dk.datestuff.hunt.Orientation>) {
    when (step.type) {
        PhoneStepType.GPS -> GpsStep(step, loc, orientation, silent = false)
        PhoneStepType.GEOFENCE -> GpsStep(step, loc, orientation, silent = true)
        PhoneStepType.SPRINT -> SprintStep(step, loc, orientation)
        PhoneStepType.AR_BEACON -> ArBeaconStep(step, loc, orientation)
        PhoneStepType.AR_CHEST -> ArChestStep(step)
        PhoneStepType.AR_COLLECT -> ArCollectStep(step)
        PhoneStepType.QR -> QrStep(step)
        PhoneStepType.PHOTO -> PhotoStep(step, front = false)
        PhoneStepType.SELFIE -> PhotoStep(step, front = true)
        PhoneStepType.QUESTION -> QuestionStep(step)
        PhoneStepType.COMPASS -> CompassStep(step, orientation)
        PhoneStepType.TILT -> TiltStep(step)
        PhoneStepType.WALKIE -> WalkieStep()
        else -> if (step.answers.isNotEmpty()) QuestionStep(step) else WalkieStep()
    }
}
