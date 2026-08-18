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
        isSub = isSub,
        viewCount = viewCount,
        pinned = pinned,
    )

fun EpisodeSourceDto.toDomain(): EpisodeSource =
    EpisodeSource(
        id = id,
        name = name.orEmpty(),
        host = resolveHost(),
        episodesCount = episodesCount,
    )

/**
 * [EpisodeSourceDto] несёт только имя источника — ссылки на этом шаге цепочки ещё нет
 * (она приходит позже, из `episode/target`), поэтому здесь доступен только матчинг по имени.
 *
 * Раньше в KDoc стояло, что `name` — «чистый машинный ключ (Kodik/Sibnet)». Живая выборка
 * Фазы 8 это опровергла: там же встречаются `VK Видео`, `Libria`, `Liberty`, `TSM`,
 * `Sovet (не работает)` — человекочитаемые имена. Матчинг починен внутри [VideoHost.fromKey]
 * (список алиасов вместо проверки «ключ enum'а — подстрока имени»), а окончательный хост
 * всё равно уточняется по домену в `EpisodeRepository.resolvePlaybackSource`
 * ([VideoHost.resolve]).
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
