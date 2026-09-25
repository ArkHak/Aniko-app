package com.aniko.data.update

/**
 * Причина сбоя проверки/загрузки/установки обновления — без готового текста: текст выбирает экран
 * через i18n (ViewModel/координатор про `Strings` не знают).
 */
enum class UpdateError {
    /** Нет сети / таймаут. */
    Network,

    /** GitHub ответил 404: репозиторий закрыт или релизов ещё нет. Для пользователя — «обновлений нет». */
    NotFound,

    /** Лимит запросов GitHub без токена (403/429). */
    RateLimited,

    /** 5xx от GitHub. */
    Server,

    /** В релизе нет файла под эту платформу. */
    NoAssetForPlatform,

    /** В релизе нет `SHA256SUMS.txt` или в нём нет строки для скачиваемого файла. */
    ChecksumMissing,

    /** SHA-256 скачанного файла не совпал с `SHA256SUMS.txt` — файл удалён, ставить нельзя. */
    ChecksumMismatch,

    /** Загрузка оборвалась или ответ сервера не тот (в том числе адрес загрузки вне разрешённых хостов). */
    DownloadFailed,

    /** Не хватает места для файла обновления. */
    InsufficientStorage,

    /** Система/пользователь отклонили установку либо она не запустилась. */
    InstallRejected,

    Unknown,
}

/** Исключение с типизированной причиной для внутренних слоёв; наружу (в состояние) уходит [error]. */
class UpdateException(
    val error: UpdateError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
