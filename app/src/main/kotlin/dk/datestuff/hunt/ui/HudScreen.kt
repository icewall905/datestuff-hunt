package dk.datestuff.hunt.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.datestuff.hunt.Geo
import dk.datestuff.hunt.Game
import dk.datestuff.hunt.HuntRepo
import dk.datestuff.hunt.HuntState
import dk.datestuff.hunt.Step
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HudScreen(onStep: (String) -> Unit, onUnpair: () -> Unit) {
    val state by HuntRepo.state.collectAsStateWithLifecycle()
    val loc by HuntRepo.location.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = android.os.SystemClock.elapsedRealtime() } }
    var hintDialog by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.spacedBy(8.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (state.online) Phosphor else Danger, CircleShape))
            Text("  ${state.campaign?.settings?.agent ?: "AGENT"}", color = Phosphor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.pending > 0) Tag("${state.pending} queued", Amber)
            val rem = state.remainingMs(now)
            if (rem != null) Text("  " + fmtClock(rem), color = if (rem < 0) Danger else Amber, style = MaterialTheme.typography.headlineSmall)
        }
        Text(state.campaign?.meta?.title ?: "STANDBY", color = PhosphorDim, style = MaterialTheme.typography.bodyMedium)
        // tabs
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
            listOf("MISSIONS", "INBOX${if (state.inbox.isNotEmpty()) " ${state.inbox.size}" else ""}", "CODEX", "MAP").forEachIndexed { i, t ->
                if (i == tab) HudSolidButton(t, Modifier.weight(1f)) { tab = i } else HudButton(t, Modifier.weight(1f)) { tab = i }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Missions(state, loc, onStep)
                1 -> Inbox(state)
                2 -> Codex(state)
                else -> MapTab(state, loc)
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            HudButton("Hint", Modifier.weight(1f), amber = true, enabled = state.running && state.openIds.isNotEmpty()) { hintDialog = true }
            HudButton("Unpair", Modifier.weight(1f)) { confirmUnpair = true }
        }
    }
    if (hintDialog) HintDialog(state) { hintDialog = false }
    if (confirmUnpair) AlertDialog(onDismissRequest = { confirmUnpair = false },
        confirmButton = { TextButton({ confirmUnpair = false; onUnpair() }) { Text("UNPAIR", color = Danger) } },
        dismissButton = { TextButton({ confirmUnpair = false }) { Text("CANCEL") } },
        title = { Text("Disconnect from Mission Control?") }, containerColor = Panel)
}

@Composable
private fun Missions(state: HuntState, loc: dk.datestuff.hunt.LatLng?, onStep: (String) -> Unit) {
    val s = state.session
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            s == null || s.state == "idle" -> item { HudPanel { Text("STANDBY. AWAITING MISSION CONTROL.", color = Amber, style = MaterialTheme.typography.titleMedium) } }
            s.state == "done" -> item {
                HudPanel(accent = Phosphor) {
                    Text(state.campaign?.settings?.endLine ?: "MISSION COMPLETE.", color = Phosphor, style = MaterialTheme.typography.headlineSmall)
                    state.campaign?.settings?.finalMessage?.let { Lines(it) }
                }
            }
            else -> {
                if (s.state == "paused") item { HudPanel(accent = Amber) { Text("OPERATION PAUSED", color = Amber, style = MaterialTheme.typography.titleMedium) } }
                val phone = state.openPhoneSteps
                if (phone.isEmpty()) item {
                    val other = state.openIds.size
                    HudPanel { Text(if (other > 0) "NO FIELD TASKS. $other TASK(S) OPEN AT THE TERMINAL." else "NO OPEN TASKS.", color = PhosphorDim, style = MaterialTheme.typography.bodyLarge) }
                }
                items(phone, key = { it.id }) { st -> StepCard(st, loc) { onStep(st.id) } }
            }
        }
    }
}

@Composable
private fun StepCard(st: Step, loc: dk.datestuff.hunt.LatLng?, onClick: () -> Unit) {
    HudPanel(accent = Phosphor, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(st.title.ifBlank { st.id }, color = Phosphor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Tag(st.type, Amber)
        }
        val w = st.waypoint
        if (w != null && loc != null && st.cfgBool("revealDistance", true)) {
            Text("TARGET ${Geo.formatDistance(Geo.distance(loc.lat, loc.lng, w.lat, w.lng))}", color = Amber, style = MaterialTheme.typography.bodyLarge)
        }
        st.text.firstOrNull()?.let { Text(it, color = PhosphorDim, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
    }
}

@Composable
private fun Inbox(state: HuntState) {
    if (state.inbox.isEmpty()) { Text("NO TRANSMISSIONS.", color = PhosphorDim, style = MaterialTheme.typography.bodyLarge); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), reverseLayout = false) {
        items(state.inbox.reversed()) { m ->
            HudPanel(accent = if (m.kind == "hint") Amber else Line) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(if (m.kind == "hint") "hint" else "control", if (m.kind == "hint") Amber else Phosphor)
                    Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(m.ts)), color = PhosphorDim, style = MaterialTheme.typography.bodySmall)
                }
                Text(m.text, color = Phosphor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Codex(state: HuntState) {
    val c = state.campaign
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (c == null) { Text("NO DATA.", color = PhosphorDim); return }
        val slots = Game.wordSlots(c, state.solvedIds)
        if (slots.isNotEmpty()) {
            SectionTitle("Cipher word")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEach { ch ->
                    Text((ch ?: '_').toString(), color = if (ch != null) Amber else PhosphorDim, style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.background(Panel).padding(horizontal = 6.dp))
                }
            }
            c.settings.metaHint.forEach { Text("// $it", color = PhosphorDim, style = MaterialTheme.typography.bodyMedium) }
        }
        SectionTitle("Fragments")
        val got = state.collectedFragments()
        if (got.isEmpty()) Text("NONE YET.", color = PhosphorDim, style = MaterialTheme.typography.bodyLarge)
        got.forEach { (id, letters) ->
            HudPanel { Row { Text(letters, color = Amber, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(0.3f)); Text(state.step(id)?.title ?: id, color = Phosphor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.7f)) } }
        }
    }
}

@Composable
private fun HintDialog(state: HuntState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val steps = state.campaign?.steps?.filter { it.id in state.openIds } ?: emptyList()
    AlertDialog(
        onDismissRequest = onClose, containerColor = Panel,
        confirmButton = { TextButton(onClose) { Text("CLOSE") } },
        title = { Text("REQUEST HINT", color = Amber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result != null) Text(result!!, color = Phosphor, style = MaterialTheme.typography.bodyLarge)
                else steps.forEach { st ->
                    HudButton(st.title.ifBlank { st.id }, Modifier.fillMaxWidth(), enabled = !busy) {
                        busy = true
                        scope.launch { result = HuntRepo.requestHint(st.id) ?: "NO HINT AVAILABLE."; busy = false }
                    }
                }
            }
        },
    )
}
