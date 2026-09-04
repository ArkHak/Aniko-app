package com.aniko.app.feature.home

import com.aniko.app.mvi.UiEffect
import com.aniko.app.mvi.UiIntent
import com.aniko.app.mvi.UiState
import com.aniko.data.paging.PagingState
import com.aniko.model.AnixError
import com.aniko.model.InterestingBanner
import com.aniko.model.Release

/**
 * Простое (непагинированное) состояние секции — баннеры/«Обсуждаемое»/«Новые серии» читаются
 * одним suspend-вызовом (`ReleaseRepository.interesting`/`discussing`) или разовым снимком
 * (`ScheduleRepository.observeSchedule`), а не листингом с курсором следующей страницы —
 * полноценный [PagingState] тут избыточен (P7.T1, см. аудит Home в `docs/REELWAVE_PLAN.md`).
 */
data class SectionState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val error: AnixError? = null,
)

/**
 * Состояние главного экрана под макет (P7.T1/T2): пагинированная секция «Продолжить смотреть»
 * хранится как есть в [PagingState] соответствующего `Paginator`, непагинированные — в
 * [SectionState].
 *
 * Track C (2026-09-04): секция «Рекомендации» (была тоже [PagingState], `recommendationsPaginator`)
 * убрана целиком — макет показывает между «Продолжить смотреть» и «Новые серии» только ОДИН
 * рельс («Top This Week», см. [discussing]), а grep по репозиторию подтвердил: `HomeState.
 * recommendations`/`HomeIntent.RetryRecommendations`/`LoadMoreRecommendations` не читались нигде
 * за пределами `feature/home` — мёртвый код, безопасно удалить, а не просто перестать рендерить.
 * `ReleaseRepository.recommendations()`/`.recommendationsPaginator()`/`.observeRecommendations()`
 * (`shared/data`) и `CacheKeys.recommendations()` (`shared/database`) — тоже удалены отдельным
 * коммитом после проверки: без вызывающей стороны в приложении, не покрыты тестами, не часть
 * внешнего API (модуль `shared/data` используется только этим приложением). Низкоуровневый
 * `ReleaseApi.discoverRecommendations()` (`shared/data/api`) оставлен — документирует реальный
 * эндпоинт Anixart API, не привязан к конкретному экрану.
 */
data class HomeState(
    val watching: PagingState<Release> = PagingState(),
    /** Баннер-карусель топ-тайтлов — `discover/interesting`, без пагинации. */
    val banners: SectionState<InterestingBanner> = SectionState(),
    /** «Top This Week» (заголовок макета; данные — `discussing`, замена CUT «Top This Week»,
     *  см. P0.T3) — `discover/discussing`, фиксированный набор без пагинации. */
    val discussing: SectionState<Release> = SectionState(),
    /**
     * «Новые серии» — LOC-секция (глобального фида новых серий в API нет, см. аудит P0.T3):
     * релизы дня "сегодня" из `ScheduleRepository.observeSchedule()`, отфильтрованные по
     * `Release.myListStatus != null` (в первую очередь то, что пользователь уже смотрит), с
     * фолбэком на весь список дня, если фильтр вычистил всё.
     */
    val newEpisodes: SectionState<Release> = SectionState(),
) : UiState

/**
 * Команды экрана. Retry и loadMore для одной пагинированной секции сегодня дёргают один и тот
 * же `Paginator.loadNext()` (повторный вызов во время загрузки просто игнорируется пагинатором),
 * но остаются раздельными интентами: это разные жесты пользователя (кнопка "повторить" на ошибке
 * vs подскролл к концу ленты). Непагинированные секции (баннеры/обсуждаемое/новые серии) не имеют
 * `LoadMore*` — им нечего подгружать, только перезапросить целиком через `Retry*`.
 */
sealed interface HomeIntent : UiIntent {
    data object RetryWatching : HomeIntent

    data object LoadMoreWatching : HomeIntent

    data object RetryBanners : HomeIntent

    data object RetryDiscussing : HomeIntent

    data object RetryNewEpisodes : HomeIntent

    /** Quick-action «Случайный тайтл» — `ReleaseRepository.random()` + переход через
     *  [HomeEffect.NavigateToRelease] на успехе, [HomeEffect.ShowError] на ошибке. */
    data object OpenRandomRelease : HomeIntent
}

/**
 * Ошибка первой/повторной загрузки секции (список ещё пуст) уже видна через
 * `state.watching.error`/`state.banners.error`/`state.discussing.error`/... — экран рисует
 * по этому полю состояние ошибки на месте всей секции, эффект тут не нужен.
 *
 * [ShowError] — про другой случай: неудачная подгрузка СЛЕДУЮЩЕЙ страницы пагинированной секции,
 * когда секция уже непустая. `Paginator.loadNext()` в этом случае молча кладёт ошибку в
 * `PagingState.error`, ничего в списке не меняя, а рельса рисует список и это поле ошибки никак
 * не показывает — без эффекта пользователь не узнал бы, что подгрузка следующей страницы не
 * удалась. Заменять весь непустой список состоянием ошибки ради этого неуместно, поэтому здесь
 * снекбар/одноразовое уведомление.
 */
sealed interface HomeEffect : UiEffect {
    data class ShowError(
        val error: AnixError,
    ) : HomeEffect

    /** Случайный тайтл загружен — экран сам решает, как перейти (обычно `onReleaseClick`). */
    data class NavigateToRelease(
        val releaseId: Int,
    ) : HomeEffect
}
