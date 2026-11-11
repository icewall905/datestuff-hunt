package dk.datestuff.hunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dk.datestuff.hunt.HuntState
import kotlinx.coroutines.delay

/** Typewriter text: reveals [text] progressively. */
@Composable
fun Typewriter(text: String, msPerChar: Long = 18, color: androidx.compose.ui.graphics.Color = Phosphor, onDone: () -> Unit = {}) {
    var n by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) {
        while (n < text.length) { delay(msPerChar); n++ }
        onDone()
    }
    Text(text.take(n) + if (n < text.length) "█" else "", color = color, style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun BriefingScreen(state: HuntState, onAccept: () -> Unit) {
    val c = state.campaign
    val firstStep = state.openPhoneSteps.firstOrNull() ?: c?.steps?.firstOrNull { it.id in state.openIds }
    val chapter = c?.chapters?.firstOrNull { it.id == firstStep?.chapter } ?: c?.chapters?.firstOrNull()
    var shown by remember { mutableStateOf(0) }
    val lines = chapter?.intro ?: emptyList()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text("MISSION BRIEFING", color = Amber, style = MaterialTheme.typography.titleMedium)
        Text(c?.meta?.title ?: "OPERATION", color = Phosphor, style = MaterialTheme.typography.displaySmall)
        HudPanel { Text("AGENT: ${c?.settings?.agent ?: "?"}", color = Amber, style = MaterialTheme.typography.headlineSmall) }
        if (chapter != null) {
            SectionTitle(chapter.title)
            lines.forEachIndexed { i, l -> if (i <= shown) Typewriter(l) { if (i == shown) shown++ } }
        }
        HudSolidButton("Accept mission", Modifier.fillMaxWidth().padding(top = 12.dp), enabled = shown >= lines.size, onClick = onAccept)
        Text(if (state.online) "LINK: OK" else "LINK: LOST (cached)", color = if (state.online) PhosphorDim else Amber, style = MaterialTheme.typography.bodySmall)
    }
}
