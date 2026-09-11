package com.aniko.app.feature.feed

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.model.Article

/** Состояние экрана ленты (P16.T3, MVP) — один пагинированный список, без сортировки/фильтров/
 *  вкладок по каналам (тот же объём решения, что у [com.aniko.app.feature.notifications.
 *  NotificationsState], см. её KDoc). */
data class FeedState(
    val paging: PagingState<Article> = PagingState(),
) : UiState

/** [Load] идемпотентен — повторный вызов, пока список уже грузится/загружен, no-op, безопасно
 *  диспатчить из `LaunchedEffect(Unit)` при каждой рекомпозиции экрана. */
sealed interface FeedIntent : UiIntent {
    data object Load : FeedIntent

    data object LoadMore : FeedIntent

    data object Retry : FeedIntent
}

/** Неудачная подгрузка следующей страницы уже непустого списка — ошибка первой загрузки видна
 *  через `state.paging.error`/`AnixContentSlot` и отдельного эффекта не требует (тот же приём,
 *  что у `NotificationsEffect.ShowError`). */
sealed interface FeedEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : FeedEffect
}
