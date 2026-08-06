package com.aniko.data.api

import com.aniko.data.dto.EpisodeTargetResponseDto
import com.aniko.data.dto.EpisodesResponseDto
import com.aniko.data.dto.SimpleResponseDto
import com.aniko.data.dto.SourcesResponseDto
import com.aniko.data.dto.TypesResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

/**
 * `EpisodeApi` — цепочка резолвинга плеера:
 * `types` → `sources(typeId)` → `episodes(typeId, sourceId)` → `target(sourceId, position)`.
 */
class EpisodeApi(private val client: HttpClient) {

    /** Шаг 1: типы озвучки. `GET episode/{releaseId}` */
    suspend fun types(releaseId: Int): TypesResponseDto = apiCall {
        client.get("episode/$releaseId").body<TypesResponseDto>().requireOk()
    }

    /** Шаг 2: источники для типа. `GET episode/{releaseId}/{typeId}` */
    suspend fun sources(releaseId: Int, typeId: Int): SourcesResponseDto = apiCall {
        client.get("episode/$releaseId/$typeId").body<SourcesResponseDto>().requireOk()
    }

    /** Шаг 3: список серий. `GET episode/{releaseId}/{typeId}/{sourceId}?sort=` */
    suspend fun episodes(releaseId: Int, typeId: Int, sourceId: Int, sort: Int = 0): EpisodesResponseDto =
        apiCall {
            client.get("episode/$releaseId/$typeId/$sourceId") {
                parameter("sort", sort)
            }.body<EpisodesResponseDto>().requireOk()
        }

    /** Шаг 4: резолв серии в проигрываемый источник. `GET episode/target/{releaseId}/{sourceId}/{position}` */
    suspend fun target(releaseId: Int, sourceId: Int, position: Int): EpisodeTargetResponseDto = apiCall {
        client.get("episode/target/$releaseId/$sourceId/$position")
            .body<EpisodeTargetResponseDto>()
            .requireOk()
    }

    /** `POST episode/watch/{releaseId}/{sourceId}/{position}` */
    suspend fun markWatched(releaseId: Int, sourceId: Int, position: Int): SimpleResponseDto = apiCall {
        client.post("episode/watch/$releaseId/$sourceId/$position")
            .body<SimpleResponseDto>()
            .requireOk()
    }

    /** `POST episode/unwatch/{releaseId}/{sourceId}/{position}` */
    suspend fun markUnwatched(releaseId: Int, sourceId: Int, position: Int): SimpleResponseDto = apiCall {
        client.post("episode/unwatch/$releaseId/$sourceId/$position")
            .body<SimpleResponseDto>()
            .requireOk()
    }
}
