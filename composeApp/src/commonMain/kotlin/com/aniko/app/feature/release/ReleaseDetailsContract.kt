package com.aniko.app.feature.release

import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.Release
import com.aniko.model.ReleaseComment
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseStreamingPlatform
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
 *
 * [commentsPreview] — первые 2-3 комментария для инлайн-превью под ссылкой "N комментариев"
 * (P13.T12). Как и [details], грузится отдельным запросом (`CommentRepository.previewComments`,
 * та же живьём проверенная `GET release/comment/all/{id}/0`, что и у [ReleaseComment] на полном
 * экране комментариев) и падает молча: ошибка загрузки превью НЕ выставляет отдельное поле
 * ошибки и не блокирует остальной экран — see `ReleaseDetailsViewModel.loadCommentsPreview`,
 * превью — необязательное украшение ссылки на комментарии, а не отдельная точка входа с UI ошибок
 * (та уже есть — `ReleaseCommentsScreen`).
 *
 * [streamingPlatforms] — легальные стриминг-площадки релиза (`ReleaseRepository.
 * streamingPlatforms`, сверено вживую 2026-09-23, см. KDoc `ReleaseStreamingPlatformDto` в
 * `shared/data`), не завязано на гео. Тот же принцип, что и у [commentsPreview]: отдельный
 * независимый запрос, падает молча (`ReleaseDetailsViewModel.loadStreamingPlatforms`) — пустой
 * список ЛИБО означает «у релиза нет легальных площадок» (обычный случай, `content: []` в живых
 * сэмплах), ЛИБО «запрос ещё не завершился/упал» — экран в обоих случаях просто не рисует секцию
 * (`ReleaseStreamingPlatformsSection`), без отдельного состояния ошибки/загрузки: см. задание про
 * "падает молча, не блокирует экран" — список из необязательных площадок не самостоятельная точка
 * входа с UI ошибок, как и превью комментариев.
 */
data class ReleaseDetailsUiState(
    val isLoading: Boolean = false,
    val release: Release? = null,
    val errorMessage: LoadError? = null,
    val details: ReleaseDetails? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: LoadError? = null,
    val commentsPreview: List<ReleaseComment> = emptyList(),
    val streamingPlatforms: List<ReleaseStreamingPlatform> = emptyList(),
    // Флоу выбора серии: типы озвучки → источники → серии (см. `docs/api/ENDPOINTS.md`).
    val voiceTypes: List<VoiceType> = emptyList(),
    val selectedTypeId: Int? = null,
    val sources: List<EpisodeSource> = emptyList(),
    val selectedSourceId: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val isEpisodesStepLoading: Boolean = false,
    val episodesStepError: LoadError? = null,
    // --- D4: мерж watched/unwatched для EpisodeGrid текущего selectedSourceId ---
    // Весь известный локальный статус источника (EpisodeRepository.observeWatchedPositions) —
    // позиция → is_watched, исторический оверрайд поверх Episode.isWatched с сервера. Несёт ОБЕ
    // стороны явного локального статуса (и watched=true, и явный unwatched=false), не только
    // положительную — см. KDoc [mergeWatchedOverrides].
    val watchedOverrides: Map<Int, Boolean> = emptyMap(),
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
 * 2. [ReleaseDetailsUiState.watchedOverrides] — весь известный локальный статус из
 *    `EpisodeProgressStore` (позиция → is_watched), обе стороны: и локальный watched=true бьёт
 *    серверный false, и локальный явный unwatched=false бьёт серверный true — в том числе для
 *    отметок, снятых/поставленных ДО открытия этого экрана (история, не только текущая сессия).
 * 3. `episode.isWatched` с сервера — дефолт, если позиция вообще не встречалась локально.
 */
fun List<Episode>.mergeWatchedOverrides(
    watchedOverrides: Map<Int, Boolean>,
    localToggleOverrides: Map<Int, Boolean>,
): List<Episode> {
    if (watchedOverrides.isEmpty() && localToggleOverrides.isEmpty()) return this
    return map { episode ->
        val merged =
            localToggleOverrides[episode.position]
                ?: watchedOverrides[episode.position]
                ?: episode.isWatched
        if (merged == episode.isWatched) episode else episode.copy(isWatched = merged)
    }
}

/** Готовый мердж-список для отрисовки — см. [mergeWatchedOverrides]. */
val ReleaseDetailsUiState.displayEpisodes: List<Episode>
    get() = episodes.mergeWatchedOverrides(watchedOverrides, localToggleOverrides)
