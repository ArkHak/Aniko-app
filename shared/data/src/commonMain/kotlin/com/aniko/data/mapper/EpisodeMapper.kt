package com.aniko.data.mapper

import com.aniko.data.dto.EpisodeDto
import com.aniko.data.dto.EpisodeSourceDto
import com.aniko.data.dto.EpisodeTargetDto
import com.aniko.data.dto.EpisodeTypeDto
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.EpisodeTarget
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType

fun EpisodeTypeDto.toDomain(): VoiceType =
    VoiceType(
        id = id,
        name = name.orEmpty(),
        episodesCount = episodesCount,
        workers = workers,
    )

fun EpisodeSourceDto.toDomain(): EpisodeSource =
    EpisodeSource(
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

fun EpisodeDto.toDomain(): Episode =
    Episode(
        position = position,
        name = name,
        isWatched = isWatched,
    )

fun EpisodeTargetDto.toDomain(): EpisodeTarget =
    EpisodeTarget(
        position = position,
        name = name,
        url = url,
        iframe = iframe,
    )
