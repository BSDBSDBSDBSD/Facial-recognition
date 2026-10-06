package com.faceunlock.app

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

class FaceUnlockService : LifecycleService() {

    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var prefs: Prefs
    private lateinit var keyguard: KeyguardManager
    private var processor: FaceProcessor? = null
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile private var running = false   // a detection session is active
    @Volatile private var done = false      // unlocked in this session

    // Liveness (blink) state machine
    private enum class Blink { WAIT_OPEN_1, WAIT_CLOSE, WAIT_OPEN_2, DONE }
    @Volatile private var blink = Blink.WAIT_OPEN_1

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> maybeStartSession()
                Intent.ACTION_SCREEN_OFF -> stopSession()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        startInForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        maybeStartSession()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIF_CHANNEL, "Face Unlock",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        val notif: Notification = Notification.Builder(this, Constants.NOTIF_CHANNEL)
            .setContentTitle("Face Unlock")
            .setContentText("פעיל")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, Constants.NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(Constants.NOTIF_ID, notif)
        }
    }

    private fun maybeStartSession() {
        if (running) return
        if (!prefs.serviceEnabled || !prefs.isEnrolled) return
        if (!keyguard.isKeyguardLocked) return

        running = true
        done = false
        blink = Blink.WAIT_OPEN_1
        startCamera()

        // Give up after a timeout so we don't keep the camera open forever.
        main.postDelayed({ if (!done) stopSession() }, Constants.SESSION_TIMEOUT_MS)
    }

    private fun startCamera() {
        if (processor == null) {
            processor = try {
                FaceProcessor(this)
            } catch (e: Exception) {
                Log.e(TAG, "model load failed", e); null
            } ?: run { stopSession(); return }
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { proxy -> analyze(proxy) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "startCamera failed", e)
                stopSession()
            }
        }, mainExecutorCompat())
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (done || !running) return
            val bmp = CameraUtil.upright(proxy) ?: return
            val result = processor?.process(bmp) ?: return

            val enrolled = prefs.embeddings()
            if (enrolled.isEmpty()) return
            val sim = enrolled.maxOf { FaceRecognizer.cosineSimilarity(it, result.embedding) }
            if (sim < Constants.MATCH_THRESHOLD) return

            // Face matches — require a blink for liveness (unless disabled).
            val live = if (prefs.requireBlink) advanceBlink(result) else true
            if (live) {
                done = true
                main.post { performUnlock() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "analyze error", e)
        } finally {
            proxy.close()
        }
    }

    /** Returns true once a full open->closed->open blink has been observed. */
    private fun advanceBlink(r: FaceResult): Boolean {
        val l = r.leftEyeOpen
        val rr = r.rightEyeOpen
        if (l < 0f || rr < 0f) return false   // classifier unsure; wait

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

    private fun performUnlock() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val script = prefs.customScript
        val ok = if (!script.isNullOrBlank()) {
            Unlocker.unlockWithScript(script)
        } else {
            Unlocker.unlockWithPin(prefs.pin ?: "", w, h)
        }
        Log.d(TAG, "unlock result=$ok")
        stopSession()
    }

    private fun stopSession() {
        running = false
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        stopSession()
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    private fun mainExecutorCompat() = androidx.core.content.ContextCompat.getMainExecutor(this)

    companion object {
        private const val TAG = "FaceUnlockService"

        fun start(context: Context) {
            val i = Intent(context, FaceUnlockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FaceUnlockService::class.java))
        }
    }
}
