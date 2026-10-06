package com.faceunlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the unlock service after reboot if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val prefs = Prefs(context)
            if (prefs.serviceEnabled && prefs.isEnrolled) {
                FaceUnlockService.start(context)
            }
        }
    }
}
