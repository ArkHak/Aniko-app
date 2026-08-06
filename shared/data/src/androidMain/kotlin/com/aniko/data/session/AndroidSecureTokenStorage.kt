package com.aniko.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure-хранилище токена на Android: `EncryptedSharedPreferences` поверх `MasterKey`
 * (`AES256_GCM`), ключ шифрования которого живёт в Android Keystore и никогда не покидает
 * защищённое хранилище устройства — сам файл `aniko.secure.xml` на диске лежит уже
 * зашифрованным (и ключи, и значения).
 *
 * `EncryptedSharedPreferences`/`MasterKey` помечены `@Deprecated` начиная с
 * `androidx.security:security-crypto:1.1.0-beta01` (рекомендация Google — платформенные API
 * / прямая работа с Android Keystore вместо обёртки). Полная миграция на новую схему хранения —
 * отдельная задача вне этой стадии, поэтому `@Suppress("DEPRECATION")` ниже осознанный:
 * используем эти API как есть, они по-прежнему рабочие и поддерживаемые библиотекой.
 */
@Suppress("DEPRECATION")
class AndroidSecureTokenStorage(private val context: Context) : SecureTokenStorage {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun get(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_TOKEN, null)
    }

    override suspend fun set(token: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "aniko.secure"
        const val KEY_TOKEN = "auth.token"
    }
}
