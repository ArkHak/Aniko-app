package com.aniko.data.repository

import com.aniko.data.api.ScheduleApi
import com.aniko.data.cache.Cached
import com.aniko.data.cache.ReleaseCacheStores
import com.aniko.data.cache.cacheFirstFlow
import com.aniko.data.cache.hydratePagedIds
import com.aniko.data.cache.persistPagedReleases
import com.aniko.data.mapper.toDomain
import com.aniko.database.cache.CacheKeys
import com.aniko.database.cache.CachePolicy
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.ReleaseCacheStore
import com.aniko.database.store.ReleaseListStore
import com.aniko.model.Paged
import com.aniko.model.Release
import com.aniko.model.Schedule
import com.aniko.model.WeekDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock

/**
 * Расписание выхода эпизодов по дням недели (P4.T7, S3 — первый потребитель [ScheduleApi]).
 *
 * Один сетевой ответ `GET schedule` (см. `ScheduleApi`) содержит сразу все 7 дней — локально это
 * раскладывается на 7 страниц [ReleaseListStore] (по одной на [WeekDay], ключ — `CacheKeys.schedule`),
 * чтобы переиспользовать тот же [hydratePagedIds]-хелпер, что и `ReleaseRepository`/`LibraryRepository`
 * для обычных постраничных листингов — здесь просто всегда ровно одна "страница" на день.
 */
class ScheduleRepository(
    private val scheduleApi: ScheduleApi,
    private val releaseCacheStore: ReleaseCacheStore,
    private val releaseListStore: ReleaseListStore,
    private val listMembershipStore: ListMembershipStore,
    private val clock: Clock,
) {
    private val stores = ReleaseCacheStores(releaseCacheStore, releaseListStore, listMembershipStore)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchedule(): Flow<Cached<Schedule>> {
        val days = WeekDay.entries
        val local: Flow<Schedule?> =
            combine(days.map { day -> observeDayReleases(day) }) { pagesByDay ->
                val loadedPages = pagesByDay.filterNotNull()
                if (loadedPages.size < pagesByDay.size) {
                    null
                } else {
                    Schedule(byDay = days.zip(loadedPages.map { it.items }).toMap())
                }
            }
        return cacheFirstFlow(
            local = local,
            // Один сетевой запрос пишет все 7 дней одним и тем же fetchedAt — достаточно проверить
            // свежесть по первому дню, остальные шесть штампов записаны той же транзакцией refresh().
            stampAt = { releaseListStore.fetchedAt(CacheKeys.schedule(WeekDay.MONDAY.ordinal)) },
            policy = CachePolicy.CatalogListing,
            refresh = { refreshSchedule(days) },
            clock = clock,
        )
    }

    private fun observeDayReleases(day: WeekDay): Flow<Paged<Release>?> {
        val page = releaseListStore.observePage(CacheKeys.schedule(day.ordinal))
        return hydratePagedIds(page, releaseCacheStore)
    }

    private suspend fun refreshSchedule(days: List<WeekDay>) {
        val schedule = scheduleApi.schedule().toDomain()
        val now = clock.now()
        days.forEach { day ->
            val releases = schedule.releasesOn(day)
            val page = Paged(items = releases, currentPage = 0, totalPages = 1, totalCount = releases.size)
            persistPagedReleases(CacheKeys.schedule(day.ordinal), page, stores, now)
        }
    }
}
