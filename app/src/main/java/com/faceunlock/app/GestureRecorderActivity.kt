package com.faceunlock.app

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.math.hypot

/**
 * Records the user's unlock gesture (pattern/swipe) and stores it as a root
 * script that replays it via `input touchscreen motionevent` on unlock.
 */
class GestureRecorderActivity : AppCompatActivity() {

    private lateinit var pad: GesturePadView
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture)

        pad = GesturePadView(this)
        findViewById<FrameLayout>(R.id.padContainer).addView(pad)

        btnSave = findViewById(R.id.btnSave)
        val btnClear = findViewById<MaterialButton>(R.id.btnClear)

        pad.onChanged = { btnSave.isEnabled = pad.hasGesture() }
        btnClear.setOnClickListener { pad.clear() }
        btnSave.setOnClickListener { save() }
    }

    private fun save() {
        if (!pad.hasGesture()) {
            Toast.makeText(this, "בצעו מחווה תחילה", Toast.LENGTH_SHORT).show()
            return
        }
        val script = buildScript(pad.rawPoints)
        val prefs = Prefs(this)
        prefs.customScript = script
        prefs.pin = null
        Toast.makeText(this, getString(R.string.gesture_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Build a root replay script: wake, reveal bouncer, then the recorded gesture. */
    private fun buildScript(points: List<PointF>): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels

        // Downsample to keep the command list short but faithful.
        val kept = ArrayList<PointF>()
        for (p in points) {
            if (kept.isEmpty() || hypot((p.x - kept.last().x).toDouble(), (p.y - kept.last().y).toDouble()) > 24.0) {
                kept.add(p)
            }
        }
        if (kept.last() != points.last()) kept.add(points.last())

        val lines = ArrayList<String>()
        lines.add("input keyevent 224")               // wake
        lines.add("sleep 0.6")
        // Reveal the bouncer (many lock screens need a swipe up first).
        lines.add("input touchscreen swipe ${w / 2} ${(h * 0.80).toInt()} ${w / 2} ${(h * 0.30).toInt()} 150")
        lines.add("sleep 0.5")

        val first = kept.first()
        lines.add("input touchscreen motionevent DOWN ${first.x.toInt()} ${first.y.toInt()}")
        for (i in 1 until kept.size) {
            val p = kept[i]
            lines.add("input touchscreen motionevent MOVE ${p.x.toInt()} ${p.y.toInt()}")
        }
        val last = kept.last()
        lines.add("input touchscreen motionevent UP ${last.x.toInt()} ${last.y.toInt()}")

        return lines.joinToString("\n")
    }
}
