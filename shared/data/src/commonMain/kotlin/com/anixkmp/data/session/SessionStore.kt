package com.anixkmp.data.session

import com.anixkmp.network.TokenProvider
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Хранилище токена и id профиля поверх multiplatform-settings.
 *
 * БД в MVP сознательно нет (см. план), поэтому это единственное персистентное состояние.
 * На Android/iOS стоит позже заменить backing store на EncryptedSharedPreferences / Keychain —
 * задача вне скелета.
 */
class SessionStore(private val settings: Settings) : TokenProvider {

    private val _session = MutableStateFlow(readToken())

    /** Текущий токен как поток — UI подписывается, чтобы понимать «залогинен ли». */
    val tokenFlow: StateFlow<String?> = _session.asStateFlow()

    val isAuthorized: Boolean get() = _session.value != null

    override suspend fun token(): String? = _session.value

    fun save(token: String, profileId: Long) {
        settings.putString(KEY_TOKEN, token)
        settings.putLong(KEY_PROFILE_ID, profileId)
        _session.value = token
    }

    fun profileId(): Long? =
        if (settings.hasKey(KEY_PROFILE_ID)) settings.getLong(KEY_PROFILE_ID, 0L) else null

    fun clear() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_PROFILE_ID)
        _session.value = null
    }

    private fun readToken(): String? = settings.getStringOrNull(KEY_TOKEN)

    private companion object {
        const val KEY_TOKEN = "auth.token"
        const val KEY_PROFILE_ID = "auth.profile_id"
    }
}
