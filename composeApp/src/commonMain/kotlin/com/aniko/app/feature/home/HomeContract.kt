package com.aniko.app.feature.home

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.model.Release

/** Состояние главного экрана — обе секции хранятся как есть в [com.aniko.data.paging.PagingState]
 *  из соответствующего `Paginator`, без дополнительного маппинга. */
data class HomeState(
    val watching: PagingState<Release> = PagingState(),
    val recommendations: PagingState<Release> = PagingState(),
) : UiState

/**
 * Команды экрана — зеркалят четыре публичных метода прежнего `HomeViewModel`
 * (`retryWatching`/`retryRecommendations`/`loadMoreWatching`/`loadMoreRecommendations`).
 * Retry и loadMore для одной секции сегодня дёргают один и тот же `Paginator.loadNext()`
 * (повторный вызов во время загрузки просто игнорируется пагинатором), но остаются раздельными
 * интентами: это разные жесты пользователя (кнопка "повторить" на ошибке vs подскролл к концу
 * ленты) с разных мест `HomeScreen`, и in-flight-семантика вызова у них может разойтись в
 * будущем — сейчас нет причин сращивать их в один интент.
 */
sealed interface HomeIntent : UiIntent {
    data object RetryWatching : HomeIntent

    data object RetryRecommendations : HomeIntent

    data object LoadMoreWatching : HomeIntent

    data object LoadMoreRecommendations : HomeIntent
}

/**
 * Ошибка первой/повторной загрузки секции (список ещё пуст) уже видна через
 * `state.watching.error`/`state.recommendations.error` — `HomeScreen` рисует по этому полю
 * `AnixErrorBox` на месте всей секции, эффект тут не нужен.
 *
 * [ShowError] — про другой случай: неудачная подгрузка СЛЕДУЮЩЕЙ страницы, когда секция уже
 * непустая. `Paginator.loadNext()` в этом случае молча кладёт ошибку в `PagingState.error`,
 * ничего в списке не меняя, а `ReleaseSection` в `HomeScreen` рисует `LazyRow` по ветке
 * `else` (items не пустой) и это поле ошибки никак не показывает — без эффекта пользователь
 * не узнал бы, что подгрузка следующей страницы не удалась. Заменять весь непустой список
 * `AnixErrorBox` ради этого неуместно, поэтому здесь снекбар/одноразовое уведомление.
 */
sealed interface HomeEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : HomeEffect
}
