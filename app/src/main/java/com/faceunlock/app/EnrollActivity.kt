package com.faceunlock.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class EnrollActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var processor: FaceProcessor? = null
    @Volatile private var latest: FloatArray? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previewView = PreviewView(this)
        val frame = FrameLayout(this).apply { addView(previewView) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setPadding(32, 32, 32, 48)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
        }
        status = TextView(this).apply {
            text = "מחפש פנים…"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val capture = Button(this).apply {
            text = "שמור פנים"
            setOnClickListener { save() }
        }
        bar.addView(status)
        bar.addView(capture)
        frame.addView(bar)
        setContentView(frame)

        processor = try {
            FaceProcessor(this)
        } catch (e: Exception) {
            Toast.makeText(this, "טעינת המודל נכשלה", Toast.LENGTH_LONG).show()
            finish(); null
        }

        startCamera(previewView)
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
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val bmp = proxy.toBitmapUpright() ?: return
            val r = processor?.process(bmp)
            if (r != null) {
                latest = r.embedding
                runOnUiThread { status.text = "פנים זוהו — אפשר לשמור ✓" }
            } else {
                runOnUiThread { status.text = "מחפש פנים…" }
            }
        } catch (_: Exception) {
        } finally {
            proxy.close()
        }
    }

    private fun save() {
        val e = latest
        if (e == null) {
            Toast.makeText(this, "עדיין לא זוהו פנים", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs(this).embedding = e
        Toast.makeText(this, "הפנים נשמרו", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun ImageProxy.toBitmapUpright(): Bitmap? {
        val bitmap = try {
            this.toBitmap()
        } catch (e: Exception) {
            return null
        }
        val deg = imageInfo.rotationDegrees
        if (deg == 0) return bitmap
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
