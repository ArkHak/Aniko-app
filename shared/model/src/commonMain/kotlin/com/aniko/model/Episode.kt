package com.aniko.model

/**
 * Тип озвучки/перевода — первый шаг цепочки резолвинга плеера
 * (`GET episode/{releaseId}` → `TypesResponse`).
 */
data class VoiceType(
    val id: Int,
    val name: String,
    val episodesCount: Int? = null,
    /** Живая верификация (R3): реальный API отдаёт строку (или пусто), не массив имён. */
    val workers: String? = null,
)

/**
 * Источник видео для конкретного типа озвучки
 * (`GET episode/{releaseId}/{typeId}` → `SourcesResponse`).
 *
 * @param host нормализованный хост, см. [VideoHost].
 */
data class EpisodeSource(
    val id: Int,
    val name: String,
    val host: VideoHost,
    val episodesCount: Int? = null,
)

/**
 * Серия внутри источника
 * (`GET episode/{releaseId}/{typeId}/{sourceId}` → `EpisodeResponse`).
 *
 * @param position порядковый номер, используется в `episode/target/.../{position}`.
 */
data class Episode(
    val position: Int,
    val name: String?,
    val isWatched: Boolean = false,
)

/**
 * Итог резолвинга серии в проигрываемый источник
 * (`GET episode/target/{releaseId}/{sourceId}/{position}` → `EpisodeTargetResponse`).
 *
 * @param iframe Явный признак embed-страницы против прямого потока, который отдаёт сервер.
 * Сейчас нигде не используется для ветвления: весь плеер в MVP работает через embed (WebView)
 * независимо от типа источника, см. `EpisodeRepository.resolvePlaybackSource`. Поле сохранено
 * на будущее, когда добавится нативное воспроизведение прямых потоков.
 */
data class EpisodeTarget(
    val position: Int,
    val name: String?,
    val url: String?,
    val iframe: Boolean,
)

/**
 * Хосты-плееры, встречающиеся в пакете `utils.parser` оригинального APK.
 * Часть из них отдаёт прямую ссылку, часть — только embed-страницу.
 */
enum class VideoHost(val key: String) {
    KODIK("kodik"),
    SIBNET("sibnet"),
    RUTUBE("rutube"),
    VK_VIDEO("vkvideo"),
    OK_RU("okru"),
    MAIL_RU("mailru"),
    MYVI("myvi"),
    ALLVIDEO("allvideo"),
    ANILIBRIA("anilibria"),
    SOVET_ROMANTICA("sovetromantica"),
    STUDIO_MIR("studiomir"),
    TORLOOK("torlook"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromKey(key: String?): VideoHost {
            if (key == null) return UNKNOWN
            val normalized = key.lowercase()
            return entries.firstOrNull { it.key in normalized } ?: UNKNOWN
        }
    }
}
