package com.faceunlock.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted storage for the unlock PIN and the enrolled face embeddings. */
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

    var requireBlink: Boolean
        get() = prefs.getBoolean("require_blink", true)
        set(value) = prefs.edit().putBoolean("require_blink", value).apply()

    /** Multiple enrolled face embeddings (different angles). */
    fun embeddings(): List<FloatArray> {
        val s = prefs.getString("embeddings", null) ?: return emptyList()
        if (s.isBlank()) return emptyList()
        return s.split(";").mapNotNull { row ->
            try {
                row.split(",").map { it.toFloat() }.toFloatArray()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun addEmbedding(e: FloatArray) {
        val cur = embeddings().toMutableList()
        cur.add(e)
        saveEmbeddings(cur)
    }

    fun saveEmbeddings(list: List<FloatArray>) {
        val s = list.joinToString(";") { it.joinToString(",") }
        prefs.edit().putString("embeddings", s).apply()
    }

    fun clearEmbeddings() = prefs.edit().remove("embeddings").apply()

    val faceCount: Int get() = embeddings().size

    val hasUnlockMethod: Boolean
        get() = pin != null || customScript != null

    val isEnrolled: Boolean
        get() = faceCount > 0 && hasUnlockMethod
}
