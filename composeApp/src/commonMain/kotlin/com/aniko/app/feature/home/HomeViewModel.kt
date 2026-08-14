package com.aniko.app.feature.home

import androidx.lifecycle.viewModelScope
import com.aniko.app.mvi.BaseViewModel
import com.aniko.data.paging.Paginator
import com.aniko.data.repository.ReleaseRepository
import com.aniko.data.repository.ScheduleRepository
import com.aniko.model.AnixError
import com.aniko.model.Release
import com.aniko.model.WeekDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock

/**
 * ViewModel главного экрана под макет (P7.T1/T2, MVI-контракт — см. `HomeContract.kt`).
 *
 * Пять независимых секций: два [Paginator] («Продолжить смотреть»/«Рекомендации», как и раньше,
 * P5.T8) плюс три непагинированные (баннеры/«Обсуждаемое»/«Новые серии», см. [SectionState]) —
 * каждая грузится сама по себе при создании ViewModel (см. `init`), ошибка одной секции не
 * блокирует остальные.
 */
class HomeViewModel(
    private val releaseRepository: ReleaseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clock: Clock,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>(initialState = HomeState()) {
    private val watchingPaginator = releaseRepository.watchingPaginator()
    private val recommendationsPaginator = releaseRepository.recommendationsPaginator()

    init {
        combine(watchingPaginator.state, recommendationsPaginator.state) { watching, recommendations ->
            watching to recommendations
        }.onEach { (watching, recommendations) ->
            updateState { copy(watching = watching, recommendations = recommendations) }
        }.launchIn(viewModelScope)

        dispatch(HomeIntent.LoadMoreWatching)
        dispatch(HomeIntent.LoadMoreRecommendations)
        dispatch(HomeIntent.RetryBanners)
        dispatch(HomeIntent.RetryDiscussing)
        dispatch(HomeIntent.RetryNewEpisodes)
    }

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RetryWatching, HomeIntent.LoadMoreWatching ->
                loadNextAndReportIfMoreFailed(watchingPaginator)

            HomeIntent.RetryRecommendations, HomeIntent.LoadMoreRecommendations ->
                loadNextAndReportIfMoreFailed(recommendationsPaginator)

            HomeIntent.RetryBanners -> loadBanners()
            HomeIntent.RetryDiscussing -> loadDiscussing()
            HomeIntent.RetryNewEpisodes -> loadNewEpisodes()
            HomeIntent.OpenRandomRelease -> openRandomRelease()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun openRandomRelease() {
        try {
            val release = releaseRepository.random()
            emitEffect(HomeEffect.NavigateToRelease(release.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitEffect(HomeEffect.ShowError(e.toAnixError()))
        }
    }

    /**
     * `Paginator.loadNext()` при ошибке кладёт её в свой `PagingState.error`, ничего не удаляя
     * из уже загруженных [com.aniko.data.paging.PagingState.items]. Если список к этому моменту
     * был пуст — ошибка и так видна через `HomeState.watching.error`/`.recommendations.error`.
     * Если список уже непустой — рельса это поле ошибки не показывает никак, поэтому здесь
     * дополнительно шлём [HomeEffect.ShowError] для одноразового уведомления (снекбар).
     */
    private suspend fun loadNextAndReportIfMoreFailed(paginator: Paginator<Release>) {
        paginator.loadNext()
        val state = paginator.state.value
        val error = state.error
        if (error != null && state.items.isNotEmpty()) {
            emitEffect(HomeEffect.ShowError(error))
        }
    }

    // TooGenericExceptionCaught: та же схема, что и в `ReleaseDetailsViewModel.load`/
    // `ScheduleViewModel.load` (P7-фундамент) — любая ошибка сети/API маппится в типизированный
    // `AnixError` для UI, `CancellationException` пробрасывается отдельным catch выше.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadBanners() {
        updateState { copy(banners = banners.copy(isLoading = true, error = null)) }
        try {
            val items = releaseRepository.interesting()
            updateState { copy(banners = SectionState(items = items)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateState { copy(banners = SectionState(error = e.toAnixError())) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadDiscussing() {
        updateState { copy(discussing = discussing.copy(isLoading = true, error = null)) }
        try {
            val items = releaseRepository.discussing()
            updateState { copy(discussing = SectionState(items = items)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateState { copy(discussing = SectionState(error = e.toAnixError())) }
        }
    }

    /**
     * LOC-секция «Новые серии» (см. KDoc [HomeState.newEpisodes]) — один снимок
     * `ScheduleRepository.observeSchedule()` (`.first()`: cache-first-поток эмитит максимум
     * дважды — кэш, затем сеть — и сам завершается, см. `CacheFirst.kt`; секции Home достаточно
     * первого эмита, не нужно ждать сетевой дозагрузки отдельно).
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadNewEpisodes() {
        updateState { copy(newEpisodes = newEpisodes.copy(isLoading = true, error = null)) }
        try {
            val schedule = scheduleRepository.observeSchedule().first().value
            val releasesToday = schedule.releasesOn(clock.todayWeekDay())
            val inUserLists = releasesToday.filter { it.myListStatus != null }
            val items = inUserLists.ifEmpty { releasesToday }
            updateState { copy(newEpisodes = SectionState(items = items)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateState { copy(newEpisodes = SectionState(error = e.toAnixError())) }
        }
    }
}

private fun Exception.toAnixError(): AnixError = this as? AnixError ?: AnixError.Unknown(this)

/**
 * День недели "сегодня" — без `kotlinx-datetime` (не зависимость ни `shared:model`, ни
 * `shared:data`, см. KDoc `WeekDay`), поэтому считается вручную по эпохе UTC: 1970-01-01
 * (день эпохи 0) был четвергом → порядковый номер 3 при `MONDAY = 0 .. SUNDAY = 6`.
 *
 * [TODO: verify live] Приближение по UTC, не по локальному часовому поясу устройства — около
 * полуночи по местному времени секция «Новые серии» может на короткое окно показать вчерашний/
 * завтрашний день расписания. Не блокер P7.T1: `Schedule`/`GET schedule` и так группирует только
 * по дню недели, без точного времени выхода серии (см. KDoc `Schedule`), точная локализация дня
 * не входит в контракt API.
 */
private fun Clock.todayWeekDay(): WeekDay {
    val epochDay = now().epochSeconds.floorDiv(SECONDS_PER_DAY)
    val ordinal = ((epochDay + EPOCH_DAY_ZERO_WEEKDAY_ORDINAL) % DAYS_IN_WEEK + DAYS_IN_WEEK) % DAYS_IN_WEEK
    return WeekDay.entries[ordinal.toInt()]
}

private const val SECONDS_PER_DAY = 86_400L
private const val DAYS_IN_WEEK = 7L
private const val EPOCH_DAY_ZERO_WEEKDAY_ORDINAL = 3L
