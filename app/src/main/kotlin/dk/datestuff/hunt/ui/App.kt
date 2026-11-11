package dk.datestuff.hunt.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dk.datestuff.hunt.HuntRepo
import dk.datestuff.hunt.LocationService
import dk.datestuff.hunt.Feedback
import dk.datestuff.hunt.Store
import dk.datestuff.hunt.UiEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App() {
    val ctx = LocalContext.current
    val state by HuntRepo.state.collectAsStateWithLifecycle()
    var pairLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Store.pair(ctx).collect { HuntRepo.setPair(it); pairLoaded = true }
    }
    val briefed by Store.briefed(ctx).collectAsStateWithLifecycle(initialValue = null)

    // Permissions
    val perms = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    var permTick by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    LaunchedEffect(state.pair != null) { if (state.pair != null) launcher.launch(perms) }

    // Location service while the hunt runs
    LaunchedEffect(state.running, state.pair != null, permTick) {
        if (state.running && state.pair != null) LocationService.start(ctx) else LocationService.stop(ctx)
    }

    // Banner + feedback for server events
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        HuntRepo.events.collect { e ->
            when (e) {
                is UiEvent.Solved -> { Feedback.success(ctx); banner = "STEP SOLVED" + (e.fragment?.let { " // FRAGMENT $it" } ?: "") }
                is UiEvent.Opened -> { Feedback.message(ctx); banner = "NEW ORDERS RECEIVED" }
                is UiEvent.Inbox -> { Feedback.message(ctx); banner = "MISSION CONTROL: " + e.item.text.take(80) }
                is UiEvent.Photo -> { if (e.status == "approved") Feedback.success(ctx) else if (e.status == "rejected") Feedback.error(ctx); banner = "PHOTO ${e.status.uppercase()}" }
                is UiEvent.Paused -> banner = if (e.paused) "OPERATION PAUSED" else "OPERATION RESUMED"
                is UiEvent.Approach -> { if (e.arrived) Feedback.arrive(ctx) else Feedback.approach(ctx); banner = if (e.arrived) "TARGET REACHED" else "TARGET < 50 M" }
                UiEvent.Locked -> { Feedback.error(ctx); banner = "NOT UNLOCKED YET" }
                UiEvent.Done -> { Feedback.success(ctx); banner = "MISSION COMPLETE" }
            }
        }
    }
    LaunchedEffect(banner) { if (banner != null) { delay(4000); banner = null } }

    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        when {
            !pairLoaded -> Unit
            state.pair == null -> PairScreen(onDone = { scope.launch { Store.savePair(ctx, it) } })
            state.running && state.session?.id != null && briefed != state.session?.id -> BriefingScreen(state) {
                scope.launch { Store.setBriefed(ctx, state.session?.id ?: "") }
            }
            else -> Main(onUnpair = { scope.launch { Store.clearPair(ctx); HuntRepo.logout() } })
        }
        AnimatedVisibility(banner != null, Modifier.align(Alignment.TopCenter)) {
            Text(banner ?: "", color = Bg, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().background(Amber).padding(8.dp))
        }
    }
}

@Composable
private fun Main(onUnpair: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "hud") {
        composable("hud") { HudScreen(onStep = { nav.navigate("step/$it") }, onUnpair = onUnpair) }
        composable("step/{id}") { b -> StepHost(b.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() }) }
    }
}
