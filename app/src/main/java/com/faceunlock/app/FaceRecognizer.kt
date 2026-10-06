package com.faceunlock.app

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Wraps a TFLite embedding model: Bitmap -> L2-normalized embedding.
 *
 * Input and output dimensions are read from the model itself, so this works
 * with MobileFaceNet (112x112 -> 192), a generic MobileNet embedder, etc.
 * What matters for 1:1 matching is that enrollment and verification use the
 * exact same preprocessing, which they do.
 */
class FaceRecognizer(context: Context) {

    private val interpreter: Interpreter
    val inputSize: Int
    val embeddingSize: Int

    init {
        val model = loadModel(context)
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(2) })

        val inShape = interpreter.getInputTensor(0).shape()   // [1, H, W, 3]
        inputSize = if (inShape.size >= 3) inShape[1] else 112
        val outShape = interpreter.getOutputTensor(0).shape() // [1, N]
        embeddingSize = outShape.last()
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd(Constants.MODEL_FILE)
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }

    fun embed(face: Bitmap): FloatArray {
        val size = inputSize
        val scaled = Bitmap.createScaledBitmap(face, size, size, true)

        val input = ByteBuffer.allocateDirect(4 * size * size * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        for (p in pixels) {
            val r = (p shr 16 and 0xFF)
            val g = (p shr 8 and 0xFF)
            val b = (p and 0xFF)
            input.putFloat((r - 127.5f) / 128f)
            input.putFloat((g - 127.5f) / 128f)
            input.putFloat((b - 127.5f) / 128f)
        }
        input.rewind()

        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(input, output)

        val emb = output[0]
        var norm = 0f
        for (v in emb) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) for (i in emb.indices) emb[i] /= norm
        return emb
    }

    companion object {
        /** Both vectors are L2-normalized, so dot product == cosine similarity. */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return -1f
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }
    }
}
