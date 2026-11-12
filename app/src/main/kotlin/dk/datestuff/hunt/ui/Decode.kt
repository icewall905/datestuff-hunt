package dk.datestuff.hunt.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dk.datestuff.hunt.Feedback
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Status lines shown while the photo is analysed; pure so it can be unit tested. */
object DecodeScript {
    val stages = listOf(
        "UPLINK ESTABLISHED",
        "DECODING HIDDEN IMAGE DATA…",
        "ANALYSING STEGANOGRAPHIC LAYER…",
        "CROSS-REFERENCING NIGHTINGALE CIPHER…",
        "EXTRACTING CLUE…",
    )
    /** Stage for a tick count; holds on the last stage instead of looping back to the start. */
    fun stage(tick: Int): String = stages[tick.coerceIn(0, stages.lastIndex)]
    fun hexLine(r: Random, bytes: Int = 8): String =
        (0 until bytes).joinToString(" ") { "%02X".format(r.nextInt(256)) }
}

/**
 * "Decoding hidden image data" view shown after a photo is sent.
 * status: null/"pending" = analysing, "approved" = finale then [onFinished], "rejected" = decode failed.
 */
@Composable
fun DecodeView(jpeg: ByteArray?, status: String?, queued: Boolean, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val approved = status == "approved"
    val rejected = status == "rejected"
    var tick by remember { mutableIntStateOf(0) }
    var hexSeed by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    LaunchedEffect(Unit) { while (true) { delay(140); hexSeed++ } }
    LaunchedEffect(approved, rejected, queued) {
        when {
            approved -> {
                progress.animateTo(1f, tween(900))
                Feedback.success(ctx)
                delay(2200)
                onFinished()
            }
            rejected -> Feedback.error(ctx)
            !queued -> progress.animateTo(0.9f, tween(12000, easing = LinearEasing))
        }
    }

    val inf = rememberInfiniteTransition(label = "decode")
    val scan by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    val accent = when { approved -> Phosphor; rejected -> Danger; else -> Phosphor }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(340.dp).background(Bg).border(1.dp, Line)) {
            val bmp = remember(jpeg) { jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
            if (bmp != null) {
                val green = remember {
                    ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0.30f, 0.59f, 0.11f, 0f, 0f,
                        0.10f, 0.20f, 0.04f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ))
                }
                Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    colorFilter = if (approved) null else ColorFilter.colorMatrix(green), alpha = if (approved) 1f else 0.8f)
            }
            Canvas(Modifier.fillMaxSize()) {
                val step = 24.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(Line.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height)); x += step }
                var y = 0f
                while (y < size.height) { drawLine(Line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y)); y += step }
                if (!approved && !rejected) {
                    // glitch blocks that flicker over the image
                    val r = Random(hexSeed)
                    repeat(7) {
                        val bw = step * (1 + r.nextInt(3)); val bh = step * 0.5f
                        drawRect(Phosphor.copy(alpha = 0.18f + r.nextFloat() * 0.25f),
                            Offset(r.nextFloat() * (size.width - bw), r.nextFloat() * (size.height - bh)), Size(bw, bh))
                    }
                    // sweeping scan line with a fading trail
                    val sy = scan * size.height
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Phosphor.copy(alpha = 0.35f)), sy - step * 3, sy),
                        Offset(0f, sy - step * 3), Size(size.width, step * 3))
                    drawLine(Phosphor, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 2.dp.toPx())
                }
                if (rejected) drawRect(Danger.copy(alpha = 0.25f), size = size)
                // targeting corners
                val c = 28.dp.toPx(); val w = 3.dp.toPx()
                for ((cx, cy, dx, dy) in listOf(
                    listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f),
                    listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f))) {
                    drawLine(accent, Offset(cx, cy), Offset(cx + c * dx, cy), w)
                    drawLine(accent, Offset(cx, cy), Offset(cx, cy + c * dy), w)
                }
                drawRect(accent.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
            }
        }
        val headline = when {
            approved -> "CLUE EXTRACTED"
            rejected -> "DECODE FAILED: NO MATCHING SIGNATURE"
            queued -> "SIGNAL QUEUED: DECODER STANDING BY"
            else -> DecodeScript.stage(tick)
        }
        Text(headline, color = if (rejected) Danger else if (queued) Amber else Phosphor, style = MaterialTheme.typography.titleMedium)
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRect(Line, size = size)
            drawRect(if (rejected) Danger else Phosphor, size = Size(size.width * progress.value, size.height))
        }
        if (!approved && !rejected) {
            val r = Random(hexSeed * 31 + 7)
            Text((0 until 3).joinToString("\n") { "0x%04X  %s".format(r.nextInt(0x10000), DecodeScript.hexLine(r)) },
                color = PhosphorDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
