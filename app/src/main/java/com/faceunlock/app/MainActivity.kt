package com.faceunlock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var switchBlink: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnToggle = findViewById(R.id.btnToggleService)
        switchBlink = findViewById(R.id.switchBlink)

        btnToggle.setOnClickListener { toggleService() }
        switchBlink.isChecked = prefs.requireBlink
        switchBlink.setOnCheckedChangeListener { _, v -> prefs.requireBlink = v }

        configCards()
        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun configCards() {
        configCard(
            R.id.cardFace, R.drawable.ic_face,
            getString(R.string.card_face_title), getString(R.string.card_face_sub),
            getString(R.string.btn_enroll), {
                if (hasCamera()) startActivity(Intent(this, EnrollActivity::class.java))
                else toast("צריך הרשאת מצלמה")
            },
            getString(R.string.btn_reset_faces), {
                prefs.clearEmbeddings(); toast("הפנים אופסו"); refresh()
            }
        )

        configCard(
            R.id.cardUnlock, R.drawable.ic_lock,
            getString(R.string.card_unlock_title), getString(R.string.card_unlock_sub),
            getString(R.string.btn_set_pin), { askPin() },
            getString(R.string.btn_record_gesture), {
                startActivity(Intent(this, GestureRecorderActivity::class.java))
            }
        )

        configCard(
            R.id.cardTest, R.drawable.ic_test,
            getString(R.string.card_test_title), getString(R.string.card_test_sub),
            getString(R.string.btn_test), {
                if (prefs.faceCount == 0) { toast("קודם רשמו פנים"); return@configCard }
                if (!hasCamera()) { toast("צריך הרשאת מצלמה"); return@configCard }
                startActivity(Intent(this, TestActivity::class.java))
            },
            null, null
        )

        configCard(
            R.id.cardRoot, R.drawable.ic_root,
            getString(R.string.card_root_title), getString(R.string.card_root_sub),
            getString(R.string.btn_check_root), {
                toast(if (RootShell.hasRoot()) "רוט תקין ✓" else "אין גישת רוט ✗")
            },
            null, null
        )
    }

    private fun configCard(
        cardId: Int, iconRes: Int, title: String, sub: String,
        primaryText: String, primaryAction: () -> Unit,
        secondaryText: String?, secondaryAction: (() -> Unit)?
    ) {
        val card = findViewById<View>(cardId)
        card.findViewById<ImageView>(R.id.cardIcon).setImageResource(iconRes)
        card.findViewById<TextView>(R.id.cardTitle).text = title
        card.findViewById<TextView>(R.id.cardSub).text = sub
        card.findViewById<MaterialButton>(R.id.btnPrimary).apply {
            text = primaryText
            setOnClickListener { primaryAction() }
        }
        val sec = card.findViewById<MaterialButton>(R.id.btnSecondary)
        if (secondaryText != null && secondaryAction != null) {
            sec.visibility = View.VISIBLE
            sec.text = secondaryText
            sec.setOnClickListener { secondaryAction() }
        } else {
            sec.visibility = View.GONE
        }
    }

    private fun cardInfo(cardId: Int, text: String, ok: Boolean) {
        val tv = findViewById<View>(cardId).findViewById<TextView>(R.id.cardInfo)
        tv.text = text
        tv.setTextColor(ContextCompat.getColor(this, if (ok) R.color.accent else R.color.text_secondary))
    }

    private fun askPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "הקוד הנוכחי של המכשיר"
        }
        AlertDialog.Builder(this)
            .setTitle("הגדרת PIN")
            .setMessage("הזינו את קוד הנעילה הקיים של המכשיר. הוא נשמר מוצפן ומוזרק רק אחרי זיהוי מוצלח.")
            .setView(input)
            .setPositiveButton("שמירה") { _, _ ->
                prefs.pin = input.text.toString()
                prefs.customScript = null
                toast("נשמר"); refresh()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun toggleService() {
        if (prefs.serviceEnabled) {
            prefs.serviceEnabled = false
            FaceUnlockService.stop(this)
            toast("השירות כובה")
        } else {
            if (!prefs.isEnrolled) { toast("קודם רשמו פנים והגדירו קוד/מחווה"); return }
            if (!RootShell.hasRoot()) { toast("אין רוט — הפתיחה לא תעבוד"); return }
            prefs.serviceEnabled = true
            FaceUnlockService.start(this)
            toast("השירות הופעל")
        }
        refresh()
    }

    private fun refresh() {
        val on = prefs.serviceEnabled
        statusText.text = getString(if (on) R.string.service_on else R.string.service_off)
        statusText.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.success else R.color.text_secondary)
        )
        btnToggle.setText(if (on) R.string.disable_service else R.string.enable_service)

        val n = prefs.faceCount
        cardInfo(R.id.cardFace, if (n > 0) "$n פנים ✓" else "אין", n > 0)

        val method = when {
            prefs.customScript != null -> "מחווה ✓"
            prefs.pin != null -> "PIN ✓"
            else -> "לא הוגדר"
        }
        cardInfo(R.id.cardUnlock, method, prefs.hasUnlockMethod)
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
