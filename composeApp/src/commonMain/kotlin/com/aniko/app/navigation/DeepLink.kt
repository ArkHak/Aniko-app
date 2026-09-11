package com.aniko.app.navigation

import com.aniko.model.AnixGenres
import com.aniko.model.CatalogContentType
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort

/**
 * Разбор deep-link URL (P10.T7) в [AnixDestination] — единая точка, переиспользуемая всеми тремя
 * платформенными точками входа (Android `Intent.data`, iOS `.onOpenURL`, Desktop CLI-аргумент, см.
 * `MainActivity.kt`/`iosApp/iosApp/iOSApp.swift`/`Main.kt`).
 *
 * Схема (custom scheme, НЕ `https://` App Links): `aniko://release/{id}` — карточка тайтла;
 * `aniko://catalog?...` — набор фильтров каталога (см. [parseCatalogFilterLink]; UI «Моей
 * вкладки»/шаринга ссылки, P16.T2, убран с экрана каталога 2026-09-11 — сама схема ссылки
 * оставлена: входящие ссылки старого формата продолжают открывать каталог с нужным фильтром);
 * `aniko://release/{id}/episode/{sourceId}/{position}` — карточка тайтла с попыткой сразу открыть
 * конкретную серию (см. KDoc [AnixDestination.ReleaseDetails] — почему это НЕ прямая ссылка на
 * [AnixDestination.Player]: `hostKey` неизвестен из URL, его резолвинг требует сетевой цепочки
 * типы→источники, которую делает уже сам экран).
 *
 * `https://` App Links сюда намеренно НЕ входят: они требуют Digital Asset Links
 * (`.well-known/assetlinks.json`) / `apple-app-site-association` на домене под контролем проекта, а
 * у Aniko такого домена нет (сервис ходит к чужому `api-s.anixsekai.com`, README не публикует
 * собственный домен) — придумывать домен под задачу означало бы задокументировать фикцию.
 * Custom scheme `aniko://` — единственная практичная схема при текущих вводных.
 *
 * Разбор БЕЗ платформенных URI-парсеров (`android.net.Uri`/`java.net.URI` недоступны в
 * commonMain) — простое ручное разбиение по `/`, фиксированной схемы из двух маршрутов для этого
 * достаточно, не понадобился ни один multiplatform-URI-парсер как зависимость.
 *
 * Границы (покрыты `DeepLinkTest`): неизвестная схема/пустой URL/неизвестный первый сегмент/
 * отсутствующий или нечисловой `releaseId` → `null` (ссылка не распознана). Битый или неполный
 * "episode"-хвост (нечисловой `sourceId`/`position`, отсутствующий `position`) НЕ проваливает всю
 * ссылку целиком — деградирует до обычной карточки тайтла: сам `releaseId` уже валиден, отбрасывать
 * уже распознанную часть ссылки ради строгости не имеет смысла для UX "поделились ссылкой на
 * серию — у получателя пусть откроется хотя бы карточка тайтла".
 *
 * Реализация — серия guard clauses по этим границам (`@Suppress("ReturnCount")` ниже) —
 * идиоматичнее вложенных `let`/`when` для линейного разбора, тот же приём, что и в
 * `ReleaseDetailsViewModel.resolveDeepLinkEpisodeChain`.
 */
@Suppress("ReturnCount")
fun parseDeepLink(url: String): AnixDestination? {
    val body = url.trim().removeSchemePrefixOrNull() ?: return null
    val segments =
        body
            .substringBefore('?')
            .substringBefore('#')
            .split('/')
            .filter { it.isNotBlank() }
    if (segments.isEmpty() || segments[0] != SEGMENT_RELEASE) return null

    val releaseId = segments.getOrNull(SEGMENT_INDEX_RELEASE_ID)?.toIntOrNull() ?: return null
    if (releaseId <= 0) return null
    val releaseOnly = AnixDestination.ReleaseDetails(releaseId)

    val hasEpisodeTail =
        segments.size >= EPISODE_SEGMENTS_MIN && segments[SEGMENT_INDEX_EPISODE_KEYWORD] == SEGMENT_EPISODE
    if (!hasEpisodeTail) return releaseOnly

    val sourceId = segments.getOrNull(SEGMENT_INDEX_SOURCE_ID)?.toIntOrNull()?.takeIf { it > 0 }
    val position = segments.getOrNull(SEGMENT_INDEX_POSITION)?.toIntOrNull()?.takeIf { it >= 0 }
    if (sourceId == null || position == null) return releaseOnly

    return AnixDestination.ReleaseDetails(
        releaseId = releaseId,
        pendingEpisodeSourceId = sourceId,
        pendingEpisodePosition = position,
    )
}

private fun String.removeSchemePrefixOrNull(): String? {
    if (!startsWith(DEEP_LINK_SCHEME_PREFIX, ignoreCase = true)) return null
    return substring(DEEP_LINK_SCHEME_PREFIX.length)
}

private const val DEEP_LINK_SCHEME_PREFIX = "aniko://"
private const val SEGMENT_RELEASE = "release"
private const val SEGMENT_EPISODE = "episode"

private const val SEGMENT_INDEX_RELEASE_ID = 1
private const val SEGMENT_INDEX_EPISODE_KEYWORD = 2
private const val SEGMENT_INDEX_SOURCE_ID = 3
private const val SEGMENT_INDEX_POSITION = 4

/** `["release", "{id}", "episode", "{sourceId}", "{position}"]` — минимум 5 сегментов. */
private const val EPISODE_SEGMENTS_MIN = 5

// ---- Ссылка на набор фильтров каталога (P16.T2) — только входящий разбор, см. KDoc файла ----

/**
 * Разбирает ссылку `aniko://catalog?...` в набор фильтров. `null` — это не ссылка каталога
 * (другой хост/схема/мусор).
 *
 * Отдельная функция, а не ветка [parseDeepLink]: результат здесь — не маршрут навигации, а
 * СОСТОЯНИЕ каталога, которое применяет `SearchViewModel` (через `PendingCatalogFilterLink`).
 * Возвращать вместо него «пустой» маршрут каталога значило бы врать навигации о том, что
 * произошло.
 *
 * Границы (покрыты `DeepLinkTest`): неизвестные параметры игнорируются, битые значения
 * (нечисловой статус, индекс жанра вне списка, год-не-число) отбрасываются по одному — кривое
 * значение одного фильтра не отменяет остальные, потому что частично распознанная ссылка полезнее
 * пустого каталога.
 */
@Suppress("ReturnCount") // Guard clauses по границам (схема/хост), тот же приём, что у parseDeepLink.
fun parseCatalogFilterLink(url: String): CatalogFilter? {
    val body = url.trim().removeSchemePrefixOrNull() ?: return null
    val host = body.substringBefore('?').substringBefore('#').trim('/')
    if (host != SEGMENT_CATALOG) return null
    val query = body.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    val params =
        query
            .split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', missingDelimiterValue = "").trim().lowercase()
                if (key.isEmpty()) null else key to pair.substringAfter('=', missingDelimiterValue = "")
            }.toMap()

    val genres =
        params[PARAM_GENRES]
            ?.split(',')
            ?.mapNotNull { index -> AnixGenres.popular.getOrNull(index.trim().toIntOrNull() ?: -1) }
            ?.toSet()
            ?: emptySet()
    val excludesGenres = params[PARAM_EXCLUDE].equals(PARAM_FLAG_TRUE, ignoreCase = true)

    return CatalogFilter(
        contentType =
            when (params[PARAM_TYPE]?.lowercase()) {
                PARAM_TYPE_DONGHUA -> CatalogContentType.DONGHUA
                else -> CatalogContentType.ANIME
            },
        sort =
            CatalogSort.entries.firstOrNull { it.name.equals(params[PARAM_SORT], ignoreCase = true) }
                ?: CatalogSort.POPULARITY,
        statusId = params[PARAM_STATUS]?.toIntOrNull()?.takeIf { it > 0 },
        genres = genres,
        // Режим исключения без выбранных жанров ничего не значит — не тащим его в состояние.
        genresExcludeMode = excludesGenres && genres.isNotEmpty(),
        startYear = params[PARAM_YEAR_FROM]?.toIntOrNull(),
        endYear = params[PARAM_YEAR_TO]?.toIntOrNull(),
    )
}

private const val SEGMENT_CATALOG = "catalog"
private const val PARAM_TYPE = "type"
private const val PARAM_TYPE_DONGHUA = "donghua"
private const val PARAM_SORT = "sort"
private const val PARAM_STATUS = "status"
private const val PARAM_GENRES = "genres"
private const val PARAM_EXCLUDE = "exclude"
private const val PARAM_FLAG_TRUE = "1"
private const val PARAM_YEAR_FROM = "year_from"
private const val PARAM_YEAR_TO = "year_to"
