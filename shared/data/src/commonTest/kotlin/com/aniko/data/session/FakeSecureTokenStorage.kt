package com.aniko.data.session

/**
 * Fake реализация [SecureTokenStorage] для тестов.
 *
 * Хранит токен в памяти, без I/O и без обращений к платформенным API.
 */
class FakeSecureTokenStorage : SecureTokenStorage {
    private var token: String? = null

    override suspend fun get(): String? = token

    override suspend fun set(token: String) {
        this.token = token
    }

    override suspend fun clear() {
        this.token = null
    }
}
