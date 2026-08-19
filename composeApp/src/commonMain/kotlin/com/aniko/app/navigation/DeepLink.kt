package com.aniko.app.navigation

/**
 * Разбор deep-link URL (P10.T7) в [AnixDestination] — единая точка, переиспользуемая всеми тремя
 * платформенными точками входа (Android `Intent.data`, iOS `.onOpenURL`, Desktop CLI-аргумент, см.
 * `MainActivity.kt`/`iosApp/iosApp/iOSApp.swift`/`Main.kt`).
 *
 * Схема (custom scheme, НЕ `https://` App Links): `aniko://release/{id}` — карточка тайтла;
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
