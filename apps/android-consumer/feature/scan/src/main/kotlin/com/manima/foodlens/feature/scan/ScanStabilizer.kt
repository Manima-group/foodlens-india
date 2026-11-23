package com.manima.foodlens.feature.scan

import com.manima.foodlens.core.model.Symbology

/** One barcode seen in a camera frame. Coordinates are in the upright frame (after rotation). */
data class Detection(val rawValue: String, val symbology: Symbology, val centerX: Float, val centerY: Float)

/**
 * SPEC §10.4 acceptance rules, independent of CameraX/ML Kit so they are unit-tested:
 * - several barcodes in frame: take the one whose centre is nearest the frame centre;
 * - the same value must be seen in [requiredFrames] consecutive frames;
 * - the same value is ignored for [debounceMs] after it was accepted.
 */
class ScanStabilizer(
    private val requiredFrames: Int = 2,
    private val debounceMs: Long = 2_000,
    private val now: () -> Long = System::currentTimeMillis
) {
    private var candidate: String? = null
    private var streak = 0
    private var lastAccepted: String? = null
    private var lastAcceptedAt = Long.MIN_VALUE

    fun onFrame(detections: List<Detection>, frameWidth: Int, frameHeight: Int): Detection? {
        val nearest = nearestToCentre(detections, frameWidth, frameHeight)
        if (nearest == null) {
            candidate = null
            streak = 0
            return null
        }
        if (nearest.rawValue == candidate) {
            streak++
        } else {
            candidate = nearest.rawValue
            streak = 1
        }
        if (streak < requiredFrames) return null
        streak = 0
        candidate = null
        val time = now()
        if (nearest.rawValue == lastAccepted && time - lastAcceptedAt < debounceMs) return null
        lastAccepted = nearest.rawValue
        lastAcceptedAt = time
        return nearest
    }

    /** Lets the same barcode be accepted again at once, e.g. after the user came back from the product. */
    fun reset() {
        candidate = null
        streak = 0
        lastAccepted = null
        lastAcceptedAt = Long.MIN_VALUE
    }

    companion object {
        fun nearestToCentre(detections: List<Detection>, frameWidth: Int, frameHeight: Int): Detection? {
            val cx = frameWidth / 2f
            val cy = frameHeight / 2f
            return detections.minByOrNull {
                (it.centerX - cx) * (it.centerX - cx) +
                    (it.centerY - cy) * (it.centerY - cy)
            }
        }
    }
}
