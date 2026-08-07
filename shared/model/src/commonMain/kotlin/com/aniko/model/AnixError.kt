package com.aniko.model

/**
 * Ошибки доменного слоя. Сетевой и data-слой обязаны маппить свои исключения
 * сюда, чтобы UI не зависел от Ktor/serialization.
 *
 * `message` — технический текст ТОЛЬКО для логов/debug (английский, для однозначности логов
 * независимо от локали пользователя), НЕ для показа пользователю. UI обязан показывать
 * локализованный текст: либо через `LocalStrings`-fallback по самому факту ошибки (как
 * `HomeScreen`/`LibraryScreen`/`SearchScreen`/`ProfileScreen`), либо через маппинг на типизированную
 * ошибку экрана (`LoginError`/`LoadError`/`PlayerError`, как в P2.T9) — НИКОГДА не читать
 * `error.message` напрямую в Compose-коде (P2.T10: до 2026-08-07 именно так и было, привело к
 * хардкоду русского текста в domain-слое, детектится `ForbiddenCyrillicStringLiteral`).
 */
sealed class AnixError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Нет сети / DNS / таймаут. */
    class Network(
        cause: Throwable? = null,
    ) : AnixError("No connection to the server", cause)

    /** Сервер ответил не-2xx. */
    class Http(
        val statusCode: Int,
        cause: Throwable? = null,
    ) : AnixError("Server returned status $statusCode", cause)

    /** Anixart вернул 200, но в теле — код ошибки (`code != 0`). */
    class Api(
        val apiCode: Int,
        cause: Throwable? = null,
    ) : AnixError("API returned error code $apiCode", cause)

    /** Токен отсутствует или протух. */
    class Unauthorized(
        cause: Throwable? = null,
    ) : AnixError("Sign-in required", cause)

    /** Не удалось разобрать ответ. */
    class Parsing(
        cause: Throwable? = null,
    ) : AnixError("Failed to parse the server response", cause)

    /** Не удалось получить проигрываемую ссылку из источника. */
    class PlaybackResolve(
        val host: VideoHost,
        cause: Throwable? = null,
    ) : AnixError("Failed to resolve video from source ${host.key}", cause)

    class Unknown(
        cause: Throwable? = null,
    ) : AnixError("Unknown error", cause)
}
