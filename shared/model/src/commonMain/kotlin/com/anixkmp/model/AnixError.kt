package com.anixkmp.model

/**
 * Ошибки доменного слоя. Сетевой и data-слой обязаны маппить свои исключения
 * сюда, чтобы UI не зависел от Ktor/serialization.
 */
sealed class AnixError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Нет сети / DNS / таймаут. */
    class Network(cause: Throwable? = null) : AnixError("Нет соединения с сервером", cause)

    /** Сервер ответил не-2xx. */
    class Http(val statusCode: Int, cause: Throwable? = null) :
        AnixError("Сервер вернул код $statusCode", cause)

    /** Anixart вернул 200, но в теле — код ошибки (`code != 0`). */
    class Api(val apiCode: Int, cause: Throwable? = null) :
        AnixError("API вернул код ошибки $apiCode", cause)

    /** Токен отсутствует или протух. */
    class Unauthorized(cause: Throwable? = null) : AnixError("Требуется вход в аккаунт", cause)

    /** Не удалось разобрать ответ. */
    class Parsing(cause: Throwable? = null) : AnixError("Не удалось разобрать ответ сервера", cause)

    /** Не удалось получить проигрываемую ссылку из источника. */
    class PlaybackResolve(val host: VideoHost, cause: Throwable? = null) :
        AnixError("Не удалось получить видео с источника ${host.key}", cause)

    class Unknown(cause: Throwable? = null) : AnixError("Неизвестная ошибка", cause)
}
