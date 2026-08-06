package com.anixkmp.data.mapper

import com.anixkmp.data.dto.EpisodeDto
import com.anixkmp.data.dto.EpisodeSourceDto
import com.anixkmp.data.dto.EpisodeTargetDto
import com.anixkmp.data.dto.EpisodeTypeDto
import com.anixkmp.model.Episode
import com.anixkmp.model.EpisodeSource
import com.anixkmp.model.EpisodeTarget
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
 * Живая верификация (R3, `episode/186/{typeId}`) подтвердила: [EpisodeSourceDto.name] — чистый
 * машинный ключ («Kodik», «Sibnet»), не локализованное человекочитаемое название. Прямой
 * [VideoHost.fromKey] по этому полю — рабочее решение, а не хрупкий fallback (спекулятивное
 * поле `source_key`, которое в реальном ответе не встречается, убрано из DTO).
 */
private fun EpisodeSourceDto.resolveHost(): VideoHost = VideoHost.fromKey(name)

fun EpisodeDto.toDomain(): Episode = Episode(
    position = position,
    name = name,
    isWatched = isWatched,
)

fun EpisodeTargetDto.toDomain(): EpisodeTarget = EpisodeTarget(
    position = position,
    name = name,
    url = url,
    iframe = iframe,
)
