package com.faceunlock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
        requestNeededPermissions()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "זיהוי פנים — פתיחת מכשיר"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 32, 0, 32)
        }
        root.addView(status)

        root.addView(button("רישום פנים") {
            if (hasCamera()) startActivity(Intent(this, EnrollActivity::class.java))
            else toast("צריך הרשאת מצלמה")
        })

        root.addView(button("הגדרת קוד (PIN)") { askPin() })

        root.addView(button("סקריפט פתיחה מותאם (תבנית/סיסמה)") { askScript() })

        root.addView(button("בדיקת רוט") {
            toast(if (RootShell.hasRoot()) "רוט תקין ✓" else "אין גישת רוט ✗")
        })

        root.addView(button("הפעל / כבה שירות") { toggleService() })

        return root
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
            setOnClickListener { onClick() }
        }
    }

    private fun askPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "הקוד הנוכחי של המכשיר"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("הגדרת PIN")
            .setMessage("הזן את קוד הנעילה הקיים של המכשיר. הוא נשמר מוצפן ומוזרק רק בזיהוי מוצלח.")
            .setView(input)
            .setPositiveButton("שמור") { _, _ ->
                prefs.pin = input.text.toString()
                prefs.customScript = null
                toast("נשמר")
                refreshStatus()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun askScript() {
        val input = EditText(this).apply {
            hint = "פקודות input, שורה לכל פקודה"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("סקריפט פתיחה מותאם")
            .setMessage("למתקדמים: רצף פקודות shell (למשל input swipe/keyevent) לפתיחת תבנית או סיסמה. גובר על ה-PIN.")
            .setView(input)
            .setPositiveButton("שמור") { _, _ ->
                val t = input.text.toString()
                prefs.customScript = if (t.isBlank()) null else t
                toast("נשמר")
                refreshStatus()
            }
            .setNeutralButton("נקה") { _, _ ->
                prefs.customScript = null; refreshStatus()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun toggleService() {
        if (!prefs.isEnrolled) {
            toast("צריך קודם לרשום פנים ולהגדיר קוד")
            return
        }
        if (prefs.serviceEnabled) {
            prefs.serviceEnabled = false
            FaceUnlockService.stop(this)
            toast("השירות כובה")
        } else {
            if (!RootShell.hasRoot()) {
                toast("אין רוט — הפתיחה לא תעבוד"); return
            }
            prefs.serviceEnabled = true
            FaceUnlockService.start(this)
            toast("השירות הופעל")
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val sb = StringBuilder()
        sb.append(if (prefs.embedding != null) "פנים: רשום ✓\n" else "פנים: לא רשום ✗\n")
        sb.append(
            when {
                prefs.customScript != null -> "פתיחה: סקריפט מותאם ✓\n"
                prefs.pin != null -> "פתיחה: PIN ✓\n"
                else -> "פתיחה: לא הוגדר ✗\n"
            }
        )
        sb.append(if (prefs.serviceEnabled) "שירות: פעיל ✓" else "שירות: כבוי")
        status.text = sb.toString()
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
