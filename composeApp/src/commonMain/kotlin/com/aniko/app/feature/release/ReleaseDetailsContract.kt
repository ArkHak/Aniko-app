package com.aniko.app.feature.release

import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType

/**
 * UI-состояние Title Detail (P7.T7-T13, Трек C плана).
 *
 * [release] — базовая карточка, читается cache-first через `ReleaseRepository.observeRelease`
 * (см. `ReleaseDetailsViewModel.load`) — доступна даже офлайн, если была закэширована раньше
 * (просмотр каталога/истории/избранного/предыдущего открытия этого же тайтла). [details] —
 * расширенная карточка (метаданные/скриншоты/похожее/счётчик комментариев, P7.T7/T8/T11) —
 * сетевая one-shot модель [ReleaseDetails], НЕ кэшируется (см. её KDoc в `shared/model`),
 * грузится отдельным запросом и падает независимо от [release]: [detailsError] НЕ блокирует
 * экран, если [release] уже есть что показать (D1 задания трека C) — секции, которые целиком
 * зависят от [details] (метаданные/скриншоты/похожее/рекомендуем), просто остаются пустыми.
 */
data class ReleaseDetailsUiState(
    val isLoading: Boolean = false,
    val release: Release? = null,
    val errorMessage: LoadError? = null,
    val details: ReleaseDetails? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: LoadError? = null,
    // Флоу выбора серии: типы озвучки → источники → серии (см. `docs/api/ENDPOINTS.md`).
    val voiceTypes: List<VoiceType> = emptyList(),
    val selectedTypeId: Int? = null,
    val sources: List<EpisodeSource> = emptyList(),
    val selectedSourceId: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val isEpisodesStepLoading: Boolean = false,
    val episodesStepError: LoadError? = null,
    // --- D4: мерж watched/unwatched для EpisodeGrid текущего selectedSourceId ---
    // Позиции, которые локальный стор (EpisodeRepository.observeWatchedPositions) считает
    // просмотренными — исторический оверрайд поверх Episode.isWatched с сервера. См. KDoc
    // [mergeWatchedOverrides] про известное ограничение: этот набор НЕ умеет выразить "локально
    // явно снята отметка" отдельно от "локально не трогали вообще".
    val watchedOverrides: Set<Int> = emptySet(),
    // Явные тогглы, сделанные ПРЯМО НА ЭТОМ ЭКРАНЕ за время текущей сессии (долгий тап по ячейке
    // EpisodeGrid, см. ReleaseDetailsViewModel.toggleWatched) — единственный способ в рамках
    // публичного API трека C корректно выразить обе стороны D4 ("локальный watched=true бьёт
    // серверный false" И "локальный unwatched=false бьёт серверный true"), а не только первую.
    // Сбрасывается при смене selectedSourceId — оверрайды осмысленны только в разрезе источника.
    val localToggleOverrides: Map<Int, Boolean> = emptyMap(),
    // Кнопка "Смотреть" (P7.T7) идёт по цепочке types -> sources -> episodes сама — отдельный
    // индикатор, чтобы не путать с isEpisodesStepLoading (ручной выбор чипов пользователем).
    val isResolvingPlay: Boolean = false,
)

/**
 * Причина ошибки загрузки релиза/деталей/серий, без готового текста — текст живёт в `Strings`
 * (Фаза 2 плана, P2.T9: ViewModel не знает про `LocalStrings`/Compose). Одно и то же значение
 * [GENERIC] размечает ошибки нескольких независимых шагов загрузки — какой именно текст
 * показать, решает `ReleaseDetailsScreen` по тому, в какое поле стейта попала ошибка.
 */
enum class LoadError {
    NO_CONNECTION,
    UNAUTHORIZED,
    GENERIC,
}

/** Итог резолвинга кнопки "Смотреть" (P7.T7) — параметры для перехода в плеер. */
data class PlayTarget(
    val sourceId: Int,
    val position: Int,
    val host: VideoHost,
)

/**
 * Мержит серверный `Episode.isWatched` с локальными оверрайдами для отрисовки в `EpisodeGrid`
 * (D4 задания трека C).
 *
 * Порядок приоритета на позицию:
 * 1. [ReleaseDetailsUiState.localToggleOverrides] — явный тоггл пользователя в этой сессии этого
 *    экрана, выигрывает всегда (обе стороны: и watched=true, и unwatched=false).
 * 2. [ReleaseDetailsUiState.watchedOverrides] — исторический локальный стор, ТОЛЬКО положительная
 *    сторона (см. ниже про ограничение).
 * 3. `episode.isWatched` с сервера — дефолт.
 *
 * Известное ограничение фундамента (не устранимо в пределах территории трека C —
 * `EpisodeProgressStore`/`EpisodeRepository` в `shared/database`/`shared/data` вне зоны
 * ответственности): публичный `EpisodeRepository.observeWatchedPositions` отдаёт только позиции
 * с `is_watched = 1`, поэтому "локально снятая до открытия этого экрана отметка" неотличима от
 * "локально вообще не трогали" — обе дают отсутствие в [ReleaseDetailsUiState.watchedOverrides].
 * Из-за этого через [watchedOverrides] корректно работает только половина D4 (локальный
 * watched=true бьёт серверный false). Вторую половину (локальный unwatched=false бьёт серверный
 * true) выражает [localToggleOverrides] — но только для тогглов, сделанных в текущей сессии этого
 * экрана, не для истории до его открытия.
 */
fun List<Episode>.mergeWatchedOverrides(
    watchedOverrides: Set<Int>,
    localToggleOverrides: Map<Int, Boolean>,
): List<Episode> {
    if (watchedOverrides.isEmpty() && localToggleOverrides.isEmpty()) return this
    return map { episode ->
        val merged =
            localToggleOverrides[episode.position]
                ?: (episode.position in watchedOverrides || episode.isWatched)
        if (merged == episode.isWatched) episode else episode.copy(isWatched = merged)
    }
}

/** Готовый мердж-список для отрисовки — см. [mergeWatchedOverrides]. */
val ReleaseDetailsUiState.displayEpisodes: List<Episode>
    get() = episodes.mergeWatchedOverrides(watchedOverrides, localToggleOverrides)
