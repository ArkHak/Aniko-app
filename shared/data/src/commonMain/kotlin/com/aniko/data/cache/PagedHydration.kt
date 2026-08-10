package com.aniko.data.cache

import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.ReleaseCacheStore
import com.aniko.database.store.ReleaseListStore
import com.aniko.model.Paged
import com.aniko.model.Release
import com.aniko.model.ReleaseId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * Общий хелпер для `ReleaseRepository`/`LibraryRepository`/`ScheduleRepository` (P4.T7, S3).
 *
 * [ReleaseListStore][com.aniko.database.store.ReleaseListStore] хранит листинг как упорядоченный
 * список [ReleaseId] (см. его KDoc про то, почему сами релизы не дублируются в каждой странице
 * каждого листинга) — сюда собирается `Flow<Paged<Release>?>`, читая каждый id через
 * [releaseCacheStore]`.observe`.
 *
 * `filterNotNull()`-подобная фильтрация ниже нужна на случай гонки записи/чтения: `refresh()`
 * вызывающей стороны сперва вызывает `releaseCacheStore.upsert` на каждый релиз, затем
 * `releaseListStore.replacePage` — между этими двумя шагами короткое окно, где страница уже
 * содержит id, а [ReleaseCacheStore.observe] по нему ещё эмитит `null`. Без фильтра такой id
 * потерялся бы из результирующего списка молча в самый первый эмит; на практике окно закрывается
 * следующей же эмиссией `combine`, поэтому UI её просто не увидит.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun hydratePagedIds(
    pagedIds: Flow<Paged<ReleaseId>?>,
    releaseCacheStore: ReleaseCacheStore,
): Flow<Paged<Release>?> =
    pagedIds.flatMapLatest { paged ->
        when {
            paged == null -> flowOf(null)
            paged.items.isEmpty() -> flowOf(Paged(emptyList(), paged.currentPage, paged.totalPages, paged.totalCount))
            else ->
                combine(paged.items.map { releaseCacheStore.observe(it) }) { releases ->
                    Paged(releases.filterNotNull(), paged.currentPage, paged.totalPages, paged.totalCount)
                }
        }
    }

/**
 * Три стора кэша релизов, которыми вместе пользуется [persistPagedReleases] (и любой будущий
 * подобный хелпер) — группировка вместо трёх отдельных параметров функции (детект `LongParameterList`).
 */
internal class ReleaseCacheStores(
    val releaseCacheStore: ReleaseCacheStore,
    val releaseListStore: ReleaseListStore,
    val listMembershipStore: ListMembershipStore,
)

/**
 * Общий "конец" `refresh()` для `ReleaseRepository`/`LibraryRepository`/`ScheduleRepository`
 * (P4.T7, S3) — зеркальная операция к [hydratePagedIds]: пишет свежую страницу листинга в оба
 * стора кэша (сами релизы + упорядоченный список их id под ключом [key]) и заводит первичное
 * членство в списках из данных сервера (см. [ListMembershipStore.initFromServer] — без этого
 * шага `myListStatus`/`isFavorite` в листингах никогда бы не попадали в локальную БД, найдено
 * ревью S3).
 */
internal suspend fun persistPagedReleases(
    key: String,
    paged: Paged<Release>,
    stores: ReleaseCacheStores,
    fetchedAt: Instant,
) {
    paged.items.forEach {
        stores.releaseCacheStore.upsert(it, fetchedAt)
        stores.listMembershipStore.initFromServer(it.id, it.myListStatus, it.isFavorite, fetchedAt)
    }
    stores.releaseListStore.replacePage(
        key,
        Paged(paged.items.map { it.id }, paged.currentPage, paged.totalPages, paged.totalCount),
        fetchedAt,
    )
}
