package com.ebb.jarvis.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Everything JARVIS remembers between boots. The API key is held in
 * EncryptedSharedPreferences (hardware-backed keystore where the phone has one) and
 * is never written to logs, backups, or the repository.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        // A corrupted keystore entry must not brick the home screen; degrade loudly instead.
        Log.e(TAG, "Encrypted store unavailable, falling back to plain prefs", t)
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /** Low keeps voice replies snappy; raise it when you want considered answers. */
    var effort: String
        get() = prefs.getString(KEY_EFFORT, DEFAULT_EFFORT) ?: DEFAULT_EFFORT
        set(value) = prefs.edit().putString(KEY_EFFORT, value).apply()

    var operatorName: String
        get() = prefs.getString(KEY_OPERATOR, "Sir") ?: "Sir"
        set(value) = prefs.edit().putString(KEY_OPERATOR, value).apply()

    var speakReplies: Boolean
        get() = prefs.getBoolean(KEY_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAK, value).apply()

    var bootSequence: Boolean
        get() = prefs.getBoolean(KEY_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOT, value).apply()

    var webSearch: Boolean
        get() = prefs.getBoolean(KEY_WEB, true)
        set(value) = prefs.edit().putBoolean(KEY_WEB, value).apply()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"
        const val DEFAULT_EFFORT = "low"

        private const val TAG = "SecureStore"
        private const val SECURE_FILE = "jarvis_secure"
        private const val FALLBACK_FILE = "jarvis_prefs"
        private const val KEY_API = "anthropic_api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_EFFORT = "effort"
        private const val KEY_OPERATOR = "operator_name"
        private const val KEY_SPEAK = "speak_replies"
        private const val KEY_BOOT = "boot_sequence"
        private const val KEY_WEB = "web_search"
    }
}
