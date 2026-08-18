package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `TypesResponse` — `GET episode/{releaseId}`. */
@Serializable
data class TypesResponseDto(
    override val code: Int = 0,
    val types: List<EpisodeTypeDto> = emptyList(),
) : ApiCodeAware

@Serializable
data class EpisodeTypeDto(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
    /**
     * Живая верификация (R3, `episode/186`): реальный ответ отдаёт строку
     * (`"workers":"Ancord"` либо `"workers":""`), а не массив. `List<String>` здесь ронял бы
     * десериализацию на каждом релизе — `ignoreUnknownKeys` не спасает от несовпадения типа
     * известного поля.
     */
    val workers: String? = null,
    /**
     * Живая верификация (P8.T6, `GET episode/1`): поле реально присутствует в сыром ответе —
     * `true` для типа «Субтитры», `false` для дубляжей (пример: `AniDUB` → `false`,
     * `Субтитры` → `true`). Используется для бейджа SUB и фильтра All/Dubs/Subs.
     */
    @SerialName("is_sub") val isSub: Boolean = false,
    /**
     * Живая верификация (P8.T6, `GET episode/1`): счётчик просмотров конкретной озвучки,
     * реально присутствует в ответе (пример: `AniDUB` → `51287`). Ранее задокументирован в
     * `docs/REELWAVE_PLAN.md` как незамапленный — домаплен для бейджа счётчика просмотров.
     */
    @SerialName("view_count") val viewCount: Int? = null,
    /**
     * Живая верификация (P8.T6, `GET episode/1`): булево поле есть в ответе (везде `false` на
     * проверенном релизе), семантика по имени — «закреплённая» озвучка. Используется, чтобы
     * поднять закреплённые типы озвучки в начало списка.
     */
    val pinned: Boolean = false,
)

/** `SourcesResponse` — `GET episode/{releaseId}/{typeId}`. */
@Serializable
data class SourcesResponseDto(
    override val code: Int = 0,
    val sources: List<EpisodeSourceDto> = emptyList(),
) : ApiCodeAware

@Serializable
data class EpisodeSourceDto(
    val id: Int = 0,
    /**
     * Живая верификация (R3, `episode/186/{typeId}`): значение — чистый машинный ключ
     * (`"Kodik"`, `"Sibnet"`), не локализованное человекочитаемое название. `source_key` в
     * реальном ответе не встречается вообще (было спекулятивное поле — убрано). [VideoHost.fromKey]
     * работает прямо по этому полю, без хрупкого fallback.
     */
    val name: String? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
)

/** `EpisodeResponse` — `GET episode/{releaseId}/{typeId}/{sourceId}`. */
@Serializable
data class EpisodesResponseDto(
    override val code: Int = 0,
    val episodes: List<EpisodeDto> = emptyList(),
) : ApiCodeAware

@Serializable
data class EpisodeDto(
    val position: Int = 0,
    val name: String? = null,
    @SerialName("is_watched") val isWatched: Boolean = false,
)

/**
 * `EpisodeTargetResponse` — `GET episode/target/{releaseId}/{sourceId}/{position}`.
 *
 * Живая верификация (R3): сервер отдаёт явное булево поле `iframe`. Пример — Kodik:
 * `"iframe":true, "url":"https://kodikplayer.com/seria/..."`; Sibnet: `"iframe":false,
 * "url":"https://video.sibnet.ru/shell.php?..."`.
 */
@Serializable
data class EpisodeTargetResponseDto(
    override val code: Int = 0,
    val episode: EpisodeTargetDto? = null,
) : ApiCodeAware

@Serializable
data class EpisodeTargetDto(
    val position: Int = 0,
    val name: String? = null,
    /** Ссылка на проигрываемый источник. */
    val url: String? = null,
    /**
     * Признак embed-страницы стороннего плеера против прямого потока. Пока не влияет на
     * ветвление — MVP грузит всё через embed (WebView) независимо от значения. Поле сохранено
     * на будущее нативное ветвление Direct/Embed.
     */
    val iframe: Boolean = false,
)
