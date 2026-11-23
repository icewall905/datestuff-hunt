package dk.datestuff.hunt.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import dk.datestuff.hunt.ChatLine
import dk.datestuff.hunt.buildFeed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.datestuff.hunt.Geo
import dk.datestuff.hunt.Game
import dk.datestuff.hunt.HuntRepo
import dk.datestuff.hunt.HuntState
import dk.datestuff.hunt.Step
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun HudScreen(voice: CommsVoice, onStep: (String) -> Unit, onUnpair: () -> Unit) {
    val state by HuntRepo.state.collectAsStateWithLifecycle()
    val loc by HuntRepo.location.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val draft by HuntRepo.commsDraft.collectAsStateWithLifecycle()
    LaunchedEffect(draft) { if (draft != null) tab = 1 }
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
            listOf("MISSIONS", "COMMS", "CODEX", "MAP").forEachIndexed { i, t ->
                if (i == tab) HudSolidButton(t, Modifier.weight(1f)) { tab = i } else HudButton(t, Modifier.weight(1f)) { tab = i }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Missions(state, loc, onStep)
                1 -> Comms(state, voice)
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

fun chatLabel(from: String, state: HuntState): String = when (from) {
    "handler" -> state.campaign?.settings?.handler?.name?.takeIf { it.isNotBlank() } ?: "HANDLER"
    "control" -> "CONTROL"
    else -> "YOU"
}

@Composable
private fun Comms(state: HuntState, voice: CommsVoice) {
    val feed = remember(state.serverChat, state.localChat, state.inbox) { buildFeed(state.chat, state.inbox) }
    val listState = rememberLazyListState()
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    DisposableEffect(voice) { onDispose { voice.cancelDictation() } }
    val addTranscript: (String) -> Unit = { transcript ->
        text = listOf(text.trimEnd(), transcript).filter { it.isNotEmpty() }.joinToString(" ")
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.startRecording(addTranscript)
        else voice.status = "Microphone permission denied. You can still type."
    }
    val draft by HuntRepo.commsDraft.collectAsStateWithLifecycle()
    LaunchedEffect(draft) { draft?.let { text = it; HuntRepo.consumeDraft() } }
    val extra = if (state.handlerThinking) 1 else 0
    LaunchedEffect(feed.size, extra) { if (feed.size + extra > 0) listState.animateScrollToItem(feed.size + extra - 1) }
    val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.US) }
    Column(Modifier.fillMaxSize(), Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (feed.isEmpty() && !state.handlerThinking) item { Text("NO TRANSMISSIONS.", color = PhosphorDim, style = MaterialTheme.typography.bodyLarge) }
            items(feed.size) { i ->
                val e = feed[i]
                val c = e.chat
                if (c != null) ChatBubble(
                    c, chatLabel(c.from, state), fmt.format(java.util.Date(c.ts)),
                    onRetry = { c.localId?.let(HuntRepo::retryChat) },
                    listen = { voice.listen(c.text) },
                    canListen = !voice.recording && !voice.transcribing && !voice.speaking,
                )
                else e.note?.let { m ->
                    HudPanel(accent = Amber) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tag(if (m.kind == "hint") "hint" else "control", if (m.kind == "hint") Amber else Phosphor)
                            Text(fmt.format(java.util.Date(m.ts)), color = PhosphorDim, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(m.text, color = Phosphor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        HudButton("Listen", enabled = !voice.recording && !voice.transcribing && !voice.speaking) {
                            voice.listen(m.text)
                        }
                    }
                }
            }
            if (state.handlerThinking) item { Text("${chatLabel("handler", state)} is thinking\u2026", color = PhosphorDim, style = MaterialTheme.typography.bodyLarge) }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            HudButton(
                if (voice.recording) "Finish dictation" else "Dictate",
                enabled = !voice.transcribing && state.session?.id != null,
            ) {
                if (voice.recording) voice.finishRecording(addTranscript)
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    voice.startRecording(addTranscript)
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            HudButton(if (voice.autoRead) "Voice on" else "Voice muted") {
                voice.setAutoReadEnabled(!voice.autoRead)
            }
        }
        if (voice.speaking) {
            HudButton("Stop audio") { voice.stopPlayback() }
        }
        val speechStatus = when {
            voice.recording -> "Recording up to 30 seconds."
            voice.transcribing -> "Transcribing…"
            else -> voice.status
        }
        speechStatus?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("MESSAGE", color = PhosphorDim) },
                maxLines = 3, colors = fieldColors())
            HudSolidButton("Send", enabled = text.isNotBlank() && state.session?.id != null && !voice.recording && !voice.transcribing) { HuntRepo.sendChat(text); text = "" }
        }
    }
}

@Composable
private fun ChatBubble(
    c: ChatLine, label: String, time: String, onRetry: () -> Unit,
    listen: () -> Unit, canListen: Boolean,
) {
    val mine = c.from == "agent"
    val accent = when { c.failed -> Danger; mine -> Line; c.from == "control" -> Amber; else -> Phosphor }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 300.dp).background(Panel, RoundedCornerShape(4.dp)).border(1.dp, accent, RoundedCornerShape(4.dp)).let { if (c.failed) it.clickable(onClick = onRetry) else it }.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!mine) Tag(label, if (c.from == "control") Amber else Phosphor)
                Text(if (c.failed) "FAILED \u2013 TAP TO RETRY" else if (c.pending) "SENDING\u2026" else time, color = if (c.failed) Danger else PhosphorDim, style = MaterialTheme.typography.bodySmall)
            }
            Text(c.text, color = if (c.pending) PhosphorDim else Phosphor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            if (!mine) HudButton("Listen", enabled = canListen, onClick = listen)
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
