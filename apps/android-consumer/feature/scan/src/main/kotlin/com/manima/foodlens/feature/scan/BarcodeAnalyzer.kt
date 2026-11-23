package com.manima.foodlens.feature.scan

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.manima.foodlens.core.model.Symbology
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** ML Kit restricted to retail formats (SPEC §10.4). */
internal fun newBarcodeScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
)

internal fun Barcode.symbology(): Symbology? = when (format) {
    Barcode.FORMAT_EAN_13 -> Symbology.EAN_13
    Barcode.FORMAT_EAN_8 -> Symbology.EAN_8
    Barcode.FORMAT_UPC_A -> Symbology.UPC_A
    Barcode.FORMAT_UPC_E -> Symbology.UPC_E
    else -> null
}

internal fun List<Barcode>.toDetections(): List<Detection> = mapNotNull { barcode ->
    val raw = barcode.rawValue ?: return@mapNotNull null
    val symbology = barcode.symbology() ?: return@mapNotNull null
    val box = barcode.boundingBox
    Detection(raw, symbology, box?.exactCenterX() ?: 0f, box?.exactCenterY() ?: 0f)
}

/** Feeds camera frames to ML Kit; frames are dropped while one is processed (STRATEGY_KEEP_ONLY_LATEST). */
internal class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val stabilizer: ScanStabilizer,
    private val onAccepted: (Detection) -> Unit
) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val upright = rotation % HALF_TURN == 0
        val width = if (upright) image.width else image.height
        val height = if (upright) image.height else image.width
        scanner.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { barcodes ->
                stabilizer.onFrame(barcodes.toDetections(), width, height)?.let(onAccepted)
            }
            .addOnCompleteListener { image.close() }
    }

    private companion object {
        const val HALF_TURN = 180
    }
}

/** "Scan from photo" (Photo Picker image): returns every retail barcode ML Kit finds in the still image. */
internal suspend fun detectInPhoto(context: Context, scanner: BarcodeScanner, uri: Uri): List<Detection> {
    val image = try {
        InputImage.fromFilePath(context, uri)
    } catch (_: IOException) {
        return emptyList()
    }
    return suspendCancellableCoroutine { cont ->
        scanner.process(image)
            .addOnSuccessListener { cont.resume(it.toDetections()) }
            .addOnFailureListener { cont.resume(emptyList()) }
    }
}
