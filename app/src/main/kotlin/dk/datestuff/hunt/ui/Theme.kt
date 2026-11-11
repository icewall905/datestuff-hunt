package dk.datestuff.hunt.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.datestuff.hunt.R

val Phosphor = Color(0xFF39FF7A)
val PhosphorDim = Color(0xFF1F9C4B)
val Amber = Color(0xFFFFC04A)
val Danger = Color(0xFFFF5252)
val Bg = Color(0xFF05080A)
val Panel = Color(0xFF0A1410)
val Line = Color(0xFF14502B)

val Mono = FontFamily(Font(R.font.vt323, FontWeight.Normal))

private val scheme = darkColorScheme(
    primary = Phosphor, onPrimary = Bg, secondary = Amber, onSecondary = Bg, tertiary = Amber,
    background = Bg, onBackground = Phosphor, surface = Panel, onSurface = Phosphor,
    surfaceVariant = Panel, onSurfaceVariant = PhosphorDim, outline = Line, error = Danger,
)

private fun t(size: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = w, lineHeight = (size * 1.15).sp)

private val typo = Typography(
    displayLarge = t(64), displayMedium = t(52), displaySmall = t(44),
    headlineLarge = t(38), headlineMedium = t(32), headlineSmall = t(28),
    titleLarge = t(26), titleMedium = t(22), titleSmall = t(20),
    bodyLarge = t(22), bodyMedium = t(20), bodySmall = t(17),
    labelLarge = t(20), labelMedium = t(18), labelSmall = t(16),
)

@Composable
fun HuntTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typo) {
        Box(Modifier.background(Bg)) { content() }
    }
}

@Composable
fun HudPanel(modifier: Modifier = Modifier, accent: Color = Line, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth().background(Panel, RoundedCornerShape(4.dp)).border(1.dp, accent, RoundedCornerShape(4.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(12.dp)) { content() }
}

@Composable
fun HudButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, amber: Boolean = false, onClick: () -> Unit) {
    val c = if (amber) Amber else Phosphor
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(3.dp), border = BorderStroke(1.5.dp, if (enabled) c else Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c, disabledContentColor = PhosphorDim),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text.uppercase(), style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun HudSolidButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Phosphor, contentColor = Bg, disabledContainerColor = Line, disabledContentColor = PhosphorDim),
    ) { Text(text.uppercase(), style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Phosphor, unfocusedBorderColor = Line, cursorColor = Phosphor,
    focusedTextColor = Phosphor, unfocusedTextColor = Phosphor,
    focusedLabelColor = Phosphor, unfocusedLabelColor = PhosphorDim,
)

@Composable
fun Tag(text: String, color: Color = Phosphor) {
    Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

@Composable
fun Lines(lines: List<String>, color: Color = Phosphor) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { Text(it, color = color, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
fun SectionTitle(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("> ${text.uppercase()}", color = Amber, style = MaterialTheme.typography.titleMedium)
    }
}
