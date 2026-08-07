package com.aniko.data.session

/**
 * Платформенное безопасное хранилище токена авторизации.
 *
 * Заменяет открытое хранение токена в multiplatform-settings `Settings` (см. [SessionStore]) —
 * тот бэкенд читает/пишет значение как обычный plaintext-файл, что не годится для секрета.
 * Платформенные реализации:
 * - Android — `EncryptedSharedPreferences` поверх `MasterKey` (Android Keystore, AES256_GCM);
 * - iOS — Keychain (`kSecClassGenericPassword`);
 * - Desktop/macOS — системная утилита `security` (тот же Keychain, что и на iOS/macOS).
 *
 * Намеренно НЕ `expect class`: Android-реализации нужен `Context`, а `expect class` не даёт
 * его чисто инжектить через DI. Вместо этого — обычный интерфейс, платформенные реализации
 * биндятся в Koin через `PlatformModule` (см. как это уже сделано для `Settings` в
 * `composeApp`, пакет `di`, файлы `PlatformModule.android.kt` / `PlatformModule.ios.kt` /
 * `PlatformModule.desktop.kt` — тот же приём для той же задачи).
 *
 * Методы suspend: каждая реализация выполняет блокирующий I/O (Keystore/Keychain/CLI-процесс)
 * и обязана сама уйти с вызывающего потока (`withContext(Dispatchers.IO)`) — вызывающий код
 * не должен об этом думать.
 */
interface SecureTokenStorage {
    /** Текущий сохранённый токен либо `null`, если его нет. */
    suspend fun get(): String?

    /** Сохраняет [token], заменяя предыдущее значение (если было). */
    suspend fun set(token: String)

    /** Удаляет сохранённый токен. Не ошибка, если его и не было. */
    suspend fun clear()
}
