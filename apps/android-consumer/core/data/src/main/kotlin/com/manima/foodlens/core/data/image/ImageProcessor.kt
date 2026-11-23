package com.manima.foodlens.core.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.manima.foodlens.core.data.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Pure sizing rules for [ImageProcessor], unit-tested without Android graphics. */
object ImageMath {
    const val MAX_EDGE = 2000
    const val JPEG_QUALITY = 85
    const val MIN_QUALITY = 55
    const val QUALITY_STEP = 10
    const val MAX_BYTES = 2L * 1024 * 1024

    /** Largest power-of-two subsample that still keeps the longest edge at or above [maxEdge]. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Scales so the longest edge is at most [maxEdge], keeping aspect ratio; never upscales. */
    fun targetSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    fun normalizeDegrees(degrees: Int): Int = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

    private const val FULL_TURN = 360
}

data class ProcessedImage(val file: File, val sizeBytes: Long, val width: Int, val height: Int)

/**
 * SPEC §10.6: apply rotation, strip all EXIF (re-encoding writes none, so GPS is gone), longest edge ≤ 2000 px,
 * JPEG quality 85, at most 2 MB.
 */
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun process(source: Uri, destination: File, extraRotation: Int = 0): ProcessedImage = withContext(io) {
        val resolver = context.contentResolver
        val exifRotation = resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a decodable image")
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Not a decodable image")
        val rotated = transform(decoded, ImageMath.normalizeDegrees(exifRotation + extraRotation))
        val bytes = encode(rotated)
        destination.parentFile?.mkdirs()
        destination.writeBytes(bytes)
        ProcessedImage(destination, bytes.size.toLong(), rotated.width, rotated.height).also { rotated.recycle() }
    }

    private fun transform(bitmap: Bitmap, degrees: Int): Bitmap {
        val (w, h) = ImageMath.targetSize(bitmap.width, bitmap.height)
        val matrix = Matrix().apply {
            postScale(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
            if (degrees != 0) postRotate(degrees.toFloat())
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        var current = bitmap
        var bytes = compress(current, ImageMath.JPEG_QUALITY)
        var shrinks = 0
        while (bytes.size > ImageMath.MAX_BYTES && shrinks <= MAX_SHRINKS) {
            var quality = ImageMath.JPEG_QUALITY - ImageMath.QUALITY_STEP
            while (bytes.size > ImageMath.MAX_BYTES && quality >= ImageMath.MIN_QUALITY) {
                bytes = compress(current, quality)
                quality -= ImageMath.QUALITY_STEP
            }
            if (bytes.size > ImageMath.MAX_BYTES) {
                current = current.scale((current.width * SHRINK).toInt(), (current.height * SHRINK).toInt())
                bytes = compress(current, ImageMath.JPEG_QUALITY)
            }
            shrinks++
        }
        return bytes
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private companion object {
        const val SHRINK = 0.8
        const val MAX_SHRINKS = 5
    }
}
