package com.aniko.data.repository

import com.aniko.data.api.FilterApi
import com.aniko.data.api.ReleaseApi
import com.aniko.data.api.SearchApi
import com.aniko.data.cache.Cached
import com.aniko.data.cache.ReleaseCacheStores
import com.aniko.data.cache.cacheFirstFlow
import com.aniko.data.cache.hydratePagedIds
import com.aniko.data.cache.persistPagedReleases
import com.aniko.data.dto.FilterRequestDto
import com.aniko.data.mapper.toDomain
import com.aniko.data.mapper.toReleaseDetails
import com.aniko.data.paging.Paginator
import com.aniko.database.cache.CacheKeys
import com.aniko.database.cache.CachePolicy
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.ReleaseCacheStore
import com.aniko.database.store.ReleaseListStore
import com.aniko.model.AnixError
import com.aniko.model.CatalogFilter
import com.aniko.model.CatalogSort
import com.aniko.model.InterestingBanner
import com.aniko.model.Paged
import com.aniko.model.Release
import com.aniko.model.ReleaseDetails
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

// Фасад над всеми эндпоинтами релизов (карточка/детали/списки главного экрана/поиск/каталог +
// cache-first чтение) — расширяется аддитивно по мере роста Фазы 7, разбиение на несколько
// репозиториев не входит в объём этого фундамента (см. задание P7). Конструктор — по одному
// API-клиенту/store на источник данных, каждый обязателен, дефолтов нет.
@Suppress("LongParameterList", "TooManyFunctions")
class ReleaseRepository(
    private val releaseApi: ReleaseApi,
    private val searchApi: SearchApi,
    private val filterApi: FilterApi,
    private val releaseCacheStore: ReleaseCacheStore,
    private val releaseListStore: ReleaseListStore,
    private val listMembershipStore: ListMembershipStore,
    private val clock: Clock,
) {
    private val stores = ReleaseCacheStores(releaseCacheStore, releaseListStore, listMembershipStore)

    suspend fun release(releaseId: Int): Release =
        releaseApi.release(releaseId, extendedMode = true).release?.toDomain()
            ?: throw AnixError.Parsing()

    /** Расширенная карточка релиза — Title Detail (P7.T7-T13). Не кэшируется, см. `ReleaseDetails`. */
    suspend fun releaseDetails(id: Int): ReleaseDetails =
        releaseApi.release(id, extendedMode = true).release?.toReleaseDetails()
            ?: throw AnixError.Parsing()

    /** `GET release/random` — quick-action «Случайный тайтл» на Home (P7.T1). */
    suspend fun random(): Release =
        releaseApi.random().release?.toDomain()
            ?: throw AnixError.Parsing()

    suspend fun watching(page: Int): Paged<Release> = releaseApi.discoverWatching(page).toDomain { it.toDomain() }

    /** `search/releases/{page}` — см. `SearchApi` про сверенную вживую форму ответа. */
    suspend fun search(
        query: String,
        page: Int,
    ): Paged<Release> = searchApi.releaseSearch(page, query).toDomain { it.toDomain() }

    /**
     * `POST discover/discussing` — «Обсуждаемое», замена CUT «Top This Week» (см. P0.T3).
     * Без пагинации — фиксированный набор, см. KDoc `ReleaseApi.discoverDiscussing`.
     */
    suspend fun discussing(): List<Release> = releaseApi.discoverDiscussing().content.map { it.toDomain() }

    /** `POST discover/interesting` — баннеры главного экрана, без пагинации. */
    suspend fun interesting(): List<InterestingBanner> = releaseApi.discoverInteresting().content.map { it.toDomain() }

    /** Готовый пагинатор для экрана «Продолжить смотреть». */
    fun watchingPaginator(): Paginator<Release> = Paginator { page -> watching(page) }

    /** Готовый пагинатор для экрана поиска — новый на каждый поисковый запрос. */
    fun searchPaginator(query: String): Paginator<Release> = Paginator { page -> search(query, page) }

    /**
     * Готовый пагинатор каталога с расширенным фильтром (`POST filter/{page}`).
     * `CatalogSort` → `FilterRequestDto.sort` намеренно ограничен 4 значениями из мокапа
     * (desc-варианты) — см. KDoc `CatalogSort`.
     */
    fun filterPaginator(filter: CatalogFilter): Paginator<Release> = Paginator { page -> filter(filter, page) }

    private suspend fun filter(
        filter: CatalogFilter,
        page: Int,
    ): Paged<Release> {
        val request =
            FilterRequestDto(
                statusId = filter.statusId?.toLong(),
                startYear = filter.startYear,
                endYear = filter.endYear,
                sort = filter.sort.toApiSort(),
                genres = filter.genres.toList(),
                isGenresExcludeModeEnabled = filter.genresExcludeMode,
                genresMode =
                    if (filter.genresExcludeMode) {
                        FilterRequestDto.GENRES_MODE_EXCLUDE
                    } else {
                        FilterRequestDto.GENRES_MODE_ALL
                    },
            )
        return filterApi.filter(page, request).toDomain { it.toDomain() }
    }

    private fun CatalogSort.toApiSort(): Int =
        when (this) {
            CatalogSort.RECENTLY_UPDATED -> FilterRequestDto.SORT_UPDATED_DESC
            CatalogSort.RATING -> FilterRequestDto.SORT_GRADE_DESC
            CatalogSort.YEAR -> FilterRequestDto.SORT_YEAR_DESC
            CatalogSort.POPULARITY -> FilterRequestDto.SORT_POPULARITY_DESC
        }

    // ---- Cache-first чтение (P4.T7, S3) -------------------------------------------------

    /**
     * Карточка релиза — БД как SSOT, `release(releaseId)` обновляет её в фоне, см. `cacheFirstFlow`.
     * `listMembershipStore.initFromServer` заводит членство в списках из ответа сервера (см. его
     * KDoc) — без этого шага `myListStatus`/`isFavorite` никогда бы не попадали в локальную БД.
     */
    fun observeRelease(releaseId: ReleaseId): Flow<Cached<Release>> =
        cacheFirstFlow(
            local = releaseCacheStore.observe(releaseId),
            stampAt = { releaseCacheStore.fetchedAt(releaseId) },
            policy = CachePolicy.TitleMetadata,
            refresh = {
                val fetched = release(releaseId)
                val now = clock.now()
                releaseCacheStore.upsert(fetched, now)
                listMembershipStore.initFromServer(releaseId, fetched.myListStatus, fetched.isFavorite, now)
            },
            clock = clock,
        )

    fun observeWatching(page: Int): Flow<Cached<Paged<Release>>> {
        val key = CacheKeys.watching(page)
        return cacheFirstFlow(
            local = hydratePagedIds(releaseListStore.observePage(key), releaseCacheStore),
            stampAt = { releaseListStore.fetchedAt(key) },
            policy = CachePolicy.CatalogListing,
            refresh = { persistPagedReleases(key, watching(page), stores, clock.now()) },
            clock = clock,
        )
    }
}
