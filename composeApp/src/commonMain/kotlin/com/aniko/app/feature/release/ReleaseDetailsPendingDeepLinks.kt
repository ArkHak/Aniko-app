package com.aniko.app.feature.release

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aniko.model.Release
import com.aniko.model.VoiceType

/**
 * Оба pending-обработчика deep link карточки тайтла ([HandlePendingEpisodeDeepLink] +
 * [HandlePendingVoiceTypeDeepLink]) одним вызовом из `ReleaseDetailsScreen`. Вынесены из
 * `ReleaseDetailsScreen.kt` отдельным файлом: detekt `TooManyFunctions`/`LongMethod` у экрана уже
 * на грани (добавление второго вызова переваливает оба порога), а guard clauses обработчиков
 * читаются линейно только отдельными функциями (тот же приём, что и раньше у
 * `HandlePendingEpisodeDeepLink` внутри экрана).
 */
@Suppress("LongParameterList") // 10 параметров — ровно по числу входов двух объединяемых
// обработчиков (см. их собственные KDoc); группировка в конфиг-класс добавила бы косвенность
// ради одного вызова на экране.
@Composable
internal fun ReleaseDetailsPendingDeepLinks(
    releaseId: Int,
    release: Release?,
    voiceTypes: List<VoiceType>,
    selectedTypeId: Int?,
    pendingEpisodeSourceId: Int?,
    pendingEpisodePosition: Int?,
    pendingVoiceTypeId: Int?,
    resolveDeepLinkEpisode: suspend (sourceId: Int, position: Int) -> PlayTarget?,
    onDeepLinkEpisodeResolved: (PlayTarget) -> Unit,
    selectVoiceType: (Int) -> Unit,
) {
    HandlePendingEpisodeDeepLink(
        releaseId = releaseId,
        release = release,
        sourceId = pendingEpisodeSourceId,
        position = pendingEpisodePosition,
        resolve = resolveDeepLinkEpisode,
        onResolved = onDeepLinkEpisodeResolved,
    )
    HandlePendingVoiceTypeDeepLink(
        releaseId = releaseId,
        voiceTypes = voiceTypes,
        typeId = pendingVoiceTypeId,
        selectedTypeId = selectedTypeId,
        selectVoiceType = selectVoiceType,
    )
}

/**
 * Deep link на серию (P10.T7) — резолвит `hostKey` (`ReleaseDetailsViewModel.
 * resolveDeepLinkEpisode`) и доходит до плеера сам, когда данные загрузятся, см. KDoc
 * `AnixDestination.ReleaseDetails`. Guard clauses читаются линейно, общее `&&`-условие того же
 * смысла detekt по умолчанию считает слишком сложным (`ComplexCondition`).
 *
 * Срабатывает максимум один раз на конкретную пару [sourceId]/[position] — без флага-защёлки
 * [consumed] эффект дёргался бы повторно при каждой рекомпозиции [release] (например, после
 * toggleFavorite/changeListStatus).
 */
@Suppress("LongParameterList", "ReturnCount") // 6 параметров ровно по числу входов (id/релиз/пара
// deep-link-параметров/резолвер/колбэк), 3 guard clauses читаются линейно — то же обоснование,
// что у `ReleaseDetailsViewModel.resolveDeepLinkEpisodeChain`.
@Composable
internal fun HandlePendingEpisodeDeepLink(
    releaseId: Int,
    release: Release?,
    sourceId: Int?,
    position: Int?,
    resolve: suspend (sourceId: Int, position: Int) -> PlayTarget?,
    onResolved: (PlayTarget) -> Unit,
) {
    var consumed by remember(releaseId, sourceId, position) { mutableStateOf(false) }
    if (consumed) return
    if (sourceId == null || position == null) return
    if (release == null) return

    LaunchedEffect(releaseId, sourceId, position) {
        consumed = true
        val target = resolve(sourceId, position)
        if (target != null) onResolved(target)
    }
}

/**
 * Предвыбор озвучки из deep link `aniko://release/{id}?voice={typeId}` (шеринг «верхней любимой
 * озвучки», см. `topFavoriteVoiceType` в `ReleaseDetailsContract.kt`) — выбирает чип озвучки в
 * карточке, когда типы загрузятся, БЕЗ автозапуска плеера: источники/серии подгружаются по
 * обычному флоу выбора пользователем (`ReleaseDetailsViewModel.selectVoiceType`).
 *
 * Срабатывает максимум один раз на конкретную пару (releaseId, typeId) — без флага-защёлки
 * [consumed] эффект дёргался бы повторно при каждой рекомпозиции, а `selectVoiceType` после
 * загрузки источников пересоздаёт flow выбора и сбрасывает визуальное состояние серий.
 *
 * Guard'ы (каждый — молчаливая деградация до обычной карточки, KDoc границ в `DeepLink.kt`):
 * - `typeId == null` — это не deep link с озвучкой, не наше дело;
 * - `voiceTypes` пустые — типы ещё не загрузились: ждём (эффект перезапустится при новом стейте,
 *   защёлка сработает, когда появится непустой список);
 * - `typeId` нет в `voiceTypes` — битый/устаревший id (список типов с сервера изменился с момента
 *   шеринга), предвыбирать нечего;
 * - `selectedTypeId != null` — явный выбор уже сделан (пользователь успел тапнуть, либо
 *   выбор восстановлен): deep link не должен перебивать его.
 */
@Suppress("LongParameterList", "ReturnCount") // 5 параметров ровно по числу входов (id/список
// типов/pending-id/текущий выбор/колбэк выбора), 4 guard clauses читаются линейно — то же
// обоснование, что у соседнего `HandlePendingEpisodeDeepLink`.
@Composable
internal fun HandlePendingVoiceTypeDeepLink(
    releaseId: Int,
    voiceTypes: List<VoiceType>,
    typeId: Int?,
    selectedTypeId: Int?,
    selectVoiceType: (Int) -> Unit,
) {
    var consumed by remember(releaseId, typeId) { mutableStateOf(false) }
    if (consumed) return
    if (typeId == null) return
    if (voiceTypes.isEmpty()) return
    if (voiceTypes.none { it.id == typeId }) return
    if (selectedTypeId != null) return

    LaunchedEffect(releaseId, typeId) {
        consumed = true
        selectVoiceType(typeId)
    }
}
