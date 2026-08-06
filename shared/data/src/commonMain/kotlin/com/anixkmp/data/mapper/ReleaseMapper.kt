package com.anixkmp.data.mapper

import com.anixkmp.data.dto.PageableResponseDto
import com.anixkmp.data.dto.ProfileDto
import com.anixkmp.data.dto.ReleaseDto
import com.anixkmp.model.ListStatus
import com.anixkmp.model.Paged
import com.anixkmp.model.Profile
import com.anixkmp.model.Release
import com.anixkmp.model.ReleaseStatus
import com.anixkmp.network.ApiConfig

fun ReleaseDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): Release = Release(
    id = id,
    title = titleRu?.takeIf { it.isNotBlank() } ?: titleOriginal.orEmpty(),
    originalTitle = titleOriginal,
    posterUrl = image?.toAbsoluteUrl(staticBaseUrl),
    description = description,
    year = year?.toIntOrNull(),
    episodesTotal = episodesTotal,
    episodesReleased = episodesReleased,
    grade = grade,
    status = status?.name.toReleaseStatus(),
    genres = genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    myListStatus = ListStatus.fromApiValue(profileListStatus),
    isFavorite = isFavorite,
)

fun ProfileDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): Profile = Profile(
    id = id,
    login = login,
    avatarUrl = avatar?.toAbsoluteUrl(staticBaseUrl),
    isSponsor = isSponsor,
)

fun <D, T> PageableResponseDto<D>.toDomain(map: (D) -> T): Paged<T> = Paged(
    items = content.map(map),
    currentPage = currentPage,
    totalPages = totalPageCount,
    totalCount = totalCount,
)

private fun String?.toReleaseStatus(): ReleaseStatus = when {
    this == null -> ReleaseStatus.UNKNOWN
    contains("анонс", ignoreCase = true) -> ReleaseStatus.ANNOUNCE
    contains("выходит", ignoreCase = true) || contains("онгоинг", ignoreCase = true) -> ReleaseStatus.ONGOING
    contains("вышел", ignoreCase = true) || contains("заверш", ignoreCase = true) -> ReleaseStatus.FINISHED
    else -> ReleaseStatus.UNKNOWN
}
