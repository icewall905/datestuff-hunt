package dk.datestuff.hunt.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import kotlin.math.max

fun Context.has(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

/** Renders content only when [perm] is granted; otherwise a request UI. */
@Composable
fun RequirePermission(perm: String, label: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.has(perm)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) content() else {
        LaunchedEffect(Unit) { launcher.launch(perm) }
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.spacedBy(12.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
            Text("$label ACCESS REQUIRED", color = Amber, style = MaterialTheme.typography.titleLarge)
            HudButton("Grant", onClick = { launcher.launch(perm) })
        }
    }
}

val CAMERA = Manifest.permission.CAMERA
val FINE = Manifest.permission.ACCESS_FINE_LOCATION

/** Decode, rotate, downscale to max [maxSide] px and compress to JPEG. */
fun jpegFromBytes(bytes: ByteArray, rotation: Int, maxSide: Int = 1600, quality: Int = 82): ByteArray {
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (rotation != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    val big = max(bmp.width, bmp.height)
    if (big > maxSide) {
        val s = maxSide.toFloat() / big
        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
    }
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

fun fmtClock(ms: Long): String {
    val neg = ms < 0
    val s = kotlin.math.abs(ms) / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    val t = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    return if (neg) "+$t" else t
}
