package com.faceunlock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

data class FaceResult(
    val embedding: FloatArray,
    val leftEyeOpen: Float,   // -1 if unknown
    val rightEyeOpen: Float
)

/** Detects the largest face in a bitmap, crops it, and produces an embedding. */
class FaceProcessor(context: Context) {

    private val recognizer = FaceRecognizer(context)

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    /** Blocking; must be called off the main thread (e.g. on the analyzer executor). */
    fun process(bitmap: Bitmap): FaceResult? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = Tasks.await(detector.process(image))
        if (faces.isEmpty()) return null

        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val crop = cropFace(bitmap, face.boundingBox) ?: return null
        val emb = recognizer.embed(crop)

        return FaceResult(
            embedding = emb,
            leftEyeOpen = face.leftEyeOpenProbability ?: -1f,
            rightEyeOpen = face.rightEyeOpenProbability ?: -1f
        )
    }

    private fun cropFace(src: Bitmap, box: Rect): Bitmap? {
        // Add a small margin around the detected box.
        val mx = (box.width() * 0.1f).toInt()
        val my = (box.height() * 0.1f).toInt()
        val left = (box.left - mx).coerceAtLeast(0)
        val top = (box.top - my).coerceAtLeast(0)
        val right = (box.right + mx).coerceAtMost(src.width)
        val bottom = (box.bottom + my).coerceAtMost(src.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(src, left, top, w, h)
    }
}
