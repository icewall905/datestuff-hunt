package dk.datestuff.hunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dk.datestuff.hunt.Api
import dk.datestuff.hunt.PairInfo
import dk.datestuff.hunt.parsePairQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PairScreen(onDone: (PairInfo) -> Unit) {
    var scanning by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun attempt(p: PairInfo) {
        if (busy) return
        busy = true; status = "CONTACTING MISSION CONTROL..."
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Api(p.url, p.token).run { valid() && ping() } }
            busy = false
            if (ok) onDone(p) else status = "NO LINK. CHECK URL / TOKEN / NETWORK."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(12.dp)) {
        Text("FIELD AGENT", color = Phosphor, style = MaterialTheme.typography.displaySmall)
        Text("PAIR WITH MISSION CONTROL", color = Amber, style = MaterialTheme.typography.titleMedium)
        if (scanning) {
            RequirePermission(CAMERA, "CAMERA") {
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    QrScanner(Modifier.fillMaxSize()) { raw ->
                        parsePairQr(raw)?.let { scanning = false; attempt(it) }
                    }
                }
            }
            Text("Scan the pairing QR shown in Mission Control.", color = PhosphorDim, style = MaterialTheme.typography.bodyMedium)
            HudButton("Enter manually") { scanning = false }
        } else {
            OutlinedTextField(url, { url = it }, label = { Text("SERVER URL (http://host:8667)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(token, { token = it }, label = { Text("TOKEN") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            HudSolidButton("Connect", Modifier.fillMaxWidth(), enabled = !busy && url.isNotBlank() && token.isNotBlank()) {
                attempt(PairInfo(url.trim().let { if (it.startsWith("http")) it else "http://$it" }, token.trim()))
            }
            HudButton("Scan QR") { scanning = true }
        }
        if (status.isNotEmpty()) Text(status, color = Amber, style = MaterialTheme.typography.bodyLarge)
    }
}
