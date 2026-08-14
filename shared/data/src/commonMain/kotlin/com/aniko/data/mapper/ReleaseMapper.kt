package com.aniko.data.mapper

import com.aniko.data.dto.InterestingDto
import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ProfileDto
import com.aniko.data.dto.ReleaseDto
import com.aniko.model.CommunityListCounts
import com.aniko.model.InterestingBanner
import com.aniko.model.ListStatus
import com.aniko.model.Paged
import com.aniko.model.Profile
import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStatus
import com.aniko.network.ApiConfig

fun ReleaseDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): Release =
    Release(
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
        genres =
            genres
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
        myListStatus = ListStatus.fromApiValue(profileListStatus),
        isFavorite = isFavorite,
        lastViewEpisode = lastViewEpisode,
        lastViewTimestamp = lastViewTimestamp,
        episodeLastUpdate = episodeLastUpdate,
        isViewed = isViewed,
    )

/**
 * Расширенная карточка релиза (Title Detail, P7.T7-T13) — только из ответа с
 * `extended_mode=true` (`ReleaseApi.release`). См. KDoc [ReleaseDto] про два поля, где тип API
 * (`Int`) расходится с типом домена (`String`): `age_rating`/`season`.
 */
fun ReleaseDto.toReleaseDetails(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): ReleaseDetails =
    ReleaseDetails(
        release = toDomain(staticBaseUrl),
        studio = studio,
        country = country,
        author = author,
        director = director,
        season = season?.toString(),
        releaseDate = releaseDate,
        ageRating = ageRating?.toString(),
        duration = duration,
        category = category?.name,
        source = source,
        translators = translators,
        titleAlt = titleAlt,
        screenshotUrls = screenshotImages.map { it.toAbsoluteUrl(staticBaseUrl) },
        voteCounts = listOf(vote1Count, vote2Count, vote3Count, vote4Count, vote5Count),
        voteCount = voteCount,
        yourVote = yourVote,
        communityLists =
            CommunityListCounts(
                watching = watchingCount,
                plan = planCount,
                completed = completedCount,
                holdOn = holdOnCount,
                dropped = droppedCount,
                favorites = favoritesCount,
                collection = collectionCount,
            ),
        relatedReleases = relatedReleases.map { it.toDomain(staticBaseUrl) },
        recommendedReleases = recommendedReleases.map { it.toDomain(staticBaseUrl) },
        commentCount = commentCount,
        relatedCount = relatedCount,
    )

/**
 * `discover/interesting` → баннер главного экрана. `action` — строковый id цели перехода в
 * API (см. KDoc [InterestingDto]); при числовом значении разбирается как `releaseId`
 * (`toIntOrNull()` — единственный подтверждённый вживую тип цели на 2026-08-12).
 */
fun InterestingDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): InterestingBanner =
    InterestingBanner(
        id = id,
        title = title.orEmpty(),
        description = description,
        imageUrl = image?.toAbsoluteUrl(staticBaseUrl).orEmpty(),
        releaseId = action?.toIntOrNull(),
        type = type,
    )

fun ProfileDto.toDomain(staticBaseUrl: String = ApiConfig.DEFAULT_STATIC_BASE_URL): Profile =
    Profile(
        id = id,
        login = login,
        avatarUrl = avatar?.toAbsoluteUrl(staticBaseUrl),
        isSponsor = isSponsor,
    )

fun <D, T> PageableResponseDto<D>.toDomain(map: (D) -> T): Paged<T> =
    Paged(
        items = content.map(map),
        currentPage = currentPage,
        totalPages = totalPageCount,
        totalCount = totalCount,
    )

// P2.T10: это НЕ хардкод UI-текста — Anixart API отдаёт статус релиза как русскоязычную строку
// в самом ответе (нет отдельного enum-поля), поэтому парсинг обязан матчиться на её русские
// значения. `ForbiddenCyrillicStringLiteral` такие случаи и не пытается отличать (см. KDoc
// правила) — это осознанное и задокументированное исключение, не грандфазеренное через baseline.
@Suppress("ForbiddenCyrillicStringLiteral")
private fun String?.toReleaseStatus(): ReleaseStatus =
    when {
        this == null -> ReleaseStatus.UNKNOWN
        contains("анонс", ignoreCase = true) -> ReleaseStatus.ANNOUNCE
        contains("выходит", ignoreCase = true) || contains("онгоинг", ignoreCase = true) -> ReleaseStatus.ONGOING
        contains("вышел", ignoreCase = true) || contains("заверш", ignoreCase = true) -> ReleaseStatus.FINISHED
        else -> ReleaseStatus.UNKNOWN
    }
