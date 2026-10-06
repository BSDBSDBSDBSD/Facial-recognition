package com.faceunlock.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted storage for the unlock PIN and the enrolled face embedding. */
class Prefs(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "face_unlock_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var pin: String?
        get() = prefs.getString("pin", null)
        set(value) = prefs.edit().putString("pin", value).apply()

    var customScript: String?
        get() = prefs.getString("custom_script", null)
        set(value) = prefs.edit().putString("custom_script", value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    var embedding: FloatArray?
        get() {
            val s = prefs.getString("embedding", null) ?: return null
            return try {
                s.split(",").map { it.toFloat() }.toFloatArray()
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("embedding").apply()
            } else {
                prefs.edit().putString("embedding", value.joinToString(",")).apply()
            }
        }

    val isEnrolled: Boolean
        get() = embedding != null && (pin != null || customScript != null)
}
