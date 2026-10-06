package com.faceunlock.app

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

/** Dry-run: verifies recognition + blink without actually unlocking. */
class TestActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var processor: FaceProcessor? = null
    private var enrolled: List<FloatArray> = emptyList()
    private var requireBlink = true

    private enum class Blink { WAIT_OPEN_1, WAIT_CLOSE, WAIT_OPEN_2, DONE }
    @Volatile private var blink = Blink.WAIT_OPEN_1
    @Volatile private var finished = false

    private lateinit var statusText: TextView
    private lateinit var simText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        statusText = findViewById(R.id.statusText)
        simText = findViewById(R.id.simText)
        val previewView = findViewById<PreviewView>(R.id.previewView)

        val prefs = Prefs(this)
        enrolled = prefs.embeddings()
        requireBlink = prefs.requireBlink

        processor = try {
            FaceProcessor(this)
        } catch (e: Exception) {
            Toast.makeText(this, "טעינת המודל נכשלה", Toast.LENGTH_LONG).show()
            finish(); null
        }
        processor?.let { startCamera(previewView) }
    }

    private fun startCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (finished) return
            val bmp = CameraUtil.upright(proxy) ?: return
            val r = processor?.process(bmp) ?: run {
                runOnUiThread { statusText.setText(R.string.test_prompt) }
                return
            }
            if (enrolled.isEmpty()) return
            val sim = enrolled.maxOf { FaceRecognizer.cosineSimilarity(it, r.embedding) }
            runOnUiThread { simText.text = "דמיון: ${"%.2f".format(sim)}" }

            val matched = sim >= Constants.MATCH_THRESHOLD
            if (!matched) {
                runOnUiThread { statusText.text = "מחפש התאמה…" }
                return
            }

            val live = if (requireBlink) advanceBlink(r) else true
            if (!live) {
                runOnUiThread { statusText.setText(R.string.test_blink) }
                return
            }

            finished = true
            runOnUiThread {
                statusText.setText(R.string.test_success)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.success))
            }
        } catch (_: Exception) {
        } finally {
            proxy.close()
        }
    }

    private fun advanceBlink(r: FaceResult): Boolean {
        val l = r.leftEyeOpen; val rr = r.rightEyeOpen
        if (l < 0f || rr < 0f) return false
        val open = l > Constants.EYE_OPEN && rr > Constants.EYE_OPEN
        val closed = l < Constants.EYE_CLOSED && rr < Constants.EYE_CLOSED
        when (blink) {
            Blink.WAIT_OPEN_1 -> if (open) blink = Blink.WAIT_CLOSE
            Blink.WAIT_CLOSE -> if (closed) blink = Blink.WAIT_OPEN_2
            Blink.WAIT_OPEN_2 -> if (open) blink = Blink.DONE
            Blink.DONE -> {}
        }
        return blink == Blink.DONE
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
