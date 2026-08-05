package com.anixkmp.data.mapper

import com.anixkmp.data.dto.EpisodeDto
import com.anixkmp.data.dto.EpisodeSourceDto
import com.anixkmp.data.dto.EpisodeTypeDto
import com.anixkmp.model.Episode
import com.anixkmp.model.EpisodeSource
import com.anixkmp.model.VideoHost
import com.anixkmp.model.VoiceType

fun EpisodeTypeDto.toDomain(): VoiceType = VoiceType(
    id = id,
    name = name.orEmpty(),
    episodesCount = episodesCount,
    workers = workers,
)

fun EpisodeSourceDto.toDomain(): EpisodeSource = EpisodeSource(
    id = id,
    name = name.orEmpty(),
    host = resolveHost(),
    episodesCount = episodesCount,
)

/**
 * ВРЕМЕННОЕ РЕШЕНИЕ, заменить после живой верификации API (пункт R3 в `docs/api/ENDPOINTS.md`).
 *
 * Правильный путь — читать машинный ключ [EpisodeSourceDto.sourceKey]. Но точный формат
 * `SourcesResponse` ещё не сверен с реальным ответом сервера, поэтому поле приходит `null`
 * и мы падаем в fallback по человекочитаемому [EpisodeSourceDto.name] («КОДиК HD» → KODIK).
 *
 * Fallback заведомо хрупкий: название локализовано и может измениться на стороне Anixart.
 * Как только R3 даст реальное имя поля — поправить `@SerialName` в DTO и удалить fallback.
 */
private fun EpisodeSourceDto.resolveHost(): VideoHost {
    val fromExplicitKey = sourceKey?.let(VideoHost::fromKey) ?: VideoHost.UNKNOWN
    if (fromExplicitKey != VideoHost.UNKNOWN) return fromExplicitKey
    return VideoHost.fromKey(name)
}

fun EpisodeDto.toDomain(): Episode = Episode(
    position = position,
    name = name,
    isWatched = isWatched,
)
