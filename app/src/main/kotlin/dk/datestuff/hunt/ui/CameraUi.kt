package dk.datestuff.hunt.ui

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** CameraX preview with optional analysis and capture use cases. Needs CAMERA permission. */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    front: Boolean = false,
    analyzer: ImageAnalysis.Analyzer? = null,
    capture: ImageCapture? = null,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val pv = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val exec = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(front, analyzer, capture) {
        val fut = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        fut.addListener({
            val p = fut.get(); provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
            val uses = mutableListOf<androidx.camera.core.UseCase>(preview)
            if (analyzer != null) uses += ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(exec, analyzer) }
            if (capture != null) uses += capture
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, *uses.toTypedArray())
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { runCatching { provider?.unbindAll() } }
    }
    DisposableEffect(Unit) { onDispose { exec.shutdown() } }
    AndroidView({ pv }, modifier)
}

@SuppressLint("UnsafeOptInUsageError")
class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) { image.close(); return }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { list -> list.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnCompleteListener { image.close() }
    }
}

@Composable
fun QrScanner(modifier: Modifier = Modifier, onCode: (String) -> Unit) {
    val cb = rememberUpdatedState(onCode)
    val analyzer = remember { QrAnalyzer { cb.value(it) } }
    CameraPreview(modifier, analyzer = analyzer)
}

/** Takes one still, returns a resized JPEG. */
fun takeJpeg(c: android.content.Context, capture: ImageCapture, onDone: (ByteArray?) -> Unit) {
    capture.takePicture(ContextCompat.getMainExecutor(c), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bytes = runCatching {
                val buf = image.planes[0].buffer; val arr = ByteArray(buf.remaining()); buf.get(arr)
                jpegFromBytes(arr, image.imageInfo.rotationDegrees)
            }.getOrNull()
            image.close(); onDone(bytes)
        }
        override fun onError(e: ImageCaptureException) { onDone(null) }
    })
}
