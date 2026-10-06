package com.faceunlock.app

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Records the user's pattern (3x3, 9 dots) and stores it as a root script that
 * replays the connecting gesture via `input touchscreen motionevent` on unlock.
 */
class GestureRecorderActivity : AppCompatActivity() {

    private lateinit var pad: PatternPadView
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture)

        pad = PatternPadView(this)
        findViewById<FrameLayout>(R.id.padContainer).addView(pad)

        btnSave = findViewById(R.id.btnSave)
        val btnClear = findViewById<MaterialButton>(R.id.btnClear)

        pad.onChanged = { btnSave.isEnabled = pad.hasPattern() }
        btnClear.setOnClickListener { pad.clearPattern() }
        btnSave.setOnClickListener { save() }
    }

    private fun save() {
        if (!pad.hasPattern()) {
            Toast.makeText(this, "חברו לפחות 2 נקודות", Toast.LENGTH_SHORT).show()
            return
        }
        val script = buildScript(pad.selectedScreenPoints())
        val prefs = Prefs(this)
        prefs.customScript = script
        prefs.pin = null
        Toast.makeText(this, getString(R.string.gesture_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Wake, reveal the bouncer, then replay the pattern path across the dots. */
    private fun buildScript(dots: List<PointF>): String {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels

        val lines = ArrayList<String>()
        lines.add("input keyevent 224")               // wake
        lines.add("sleep 0.6")
        lines.add("input touchscreen swipe ${w / 2} ${(h * 0.80).toInt()} ${w / 2} ${(h * 0.30).toInt()} 150")
        lines.add("sleep 0.5")

        val first = dots.first()
        lines.add("input touchscreen motionevent DOWN ${first.x.toInt()} ${first.y.toInt()}")

        // Step through each dot, adding interpolated points so the path reliably
        // crosses every dot center.
        val steps = 4
        for (i in 1 until dots.size) {
            val a = dots[i - 1]
            val b = dots[i]
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                val x = (a.x + (b.x - a.x) * t).toInt()
                val y = (a.y + (b.y - a.y) * t).toInt()
                lines.add("input touchscreen motionevent MOVE $x $y")
            }
        }

        val last = dots.last()
        lines.add("input touchscreen motionevent UP ${last.x.toInt()} ${last.y.toInt()}")
        return lines.joinToString("\n")
    }
}
