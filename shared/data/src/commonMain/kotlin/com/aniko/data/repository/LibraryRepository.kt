package com.aniko.data.repository

import com.aniko.data.api.FavoriteApi
import com.aniko.data.api.HistoryApi
import com.aniko.data.api.ProfileListApi
import com.aniko.data.cache.Cached
import com.aniko.data.cache.ReleaseCacheStores
import com.aniko.data.cache.cacheFirstFlow
import com.aniko.data.cache.hydratePagedIds
import com.aniko.data.cache.persistPagedReleases
import com.aniko.data.mapper.toDomain
import com.aniko.data.paging.Paginator
import com.aniko.data.sync.SyncQueueWorker
import com.aniko.database.cache.CacheKeys
import com.aniko.database.cache.CachePolicy
import com.aniko.database.store.ListMembershipStore
import com.aniko.database.store.ReleaseCacheStore
import com.aniko.database.store.ReleaseListStore
import com.aniko.database.store.SyncQueueStore
import com.aniko.database.sync.SyncOperation
import com.aniko.database.sync.SyncOperationKind
import com.aniko.model.ListStatus
import com.aniko.model.Paged
import com.aniko.model.Release
import com.aniko.model.ReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Фаза 6 — «Списки и синхронизация»: списки по статусу, избранное и история просмотра.
 * P4.T7 (S3 — интеграция): мутации теперь оптимистично пишутся в локальную БД и уходят на сервер
 * через [SyncQueueStore]/[syncQueueWorker], а не бьют в `Api` напрямую — переживают офлайн (P4.T5).
 *
 * Токен в запросы не передаётся явно — `AnixTokenPlugin` (см. `:shared:network`) дописывает
 * `?token=` в каждый исходящий запрос сам, читая его из `TokenProvider`, поэтому здесь (как и в
 * `ReleaseRepository`/`EpisodeRepository`) о нём заботиться не нужно.
 *
 * `@Suppress("LongParameterList")`: 3 Api-класса (по одному на REST-неймспейс) + 3 стора кэша +
 * очередь + воркер + `Clock` — та же самая намеренная агрегация зависимостей одного
 * координирующего класса, что и в `SyncQueueWorker` (см. его KDoc) — не ветвящаяся логика в
 * конструкторе, разбивать ради формального лимита детекта было бы косвенностью без пользы.
 */
@Suppress("LongParameterList")
class LibraryRepository(
    private val profileListApi: ProfileListApi,
    private val favoriteApi: FavoriteApi,
    private val historyApi: HistoryApi,
    private val listMembershipStore: ListMembershipStore,
    private val releaseCacheStore: ReleaseCacheStore,
    private val releaseListStore: ReleaseListStore,
    private val syncQueueStore: SyncQueueStore,
    private val syncQueueWorker: SyncQueueWorker,
    private val clock: Clock,
) {
    private val stores = ReleaseCacheStores(releaseCacheStore, releaseListStore, listMembershipStore)

    // ---- Списки по статусу ------------------------------------------------------------

    suspend fun myList(
        status: ListStatus,
        page: Int,
        sort: Int = SORT_RECENTLY_ADDED,
    ): Paged<Release> = profileListApi.myList(status, page, sort = sort).toDomain { it.toDomain() }

    /**
     * Готовый пагинатор для экрана списка по статусу. [sort] — поставщик текущего значения
     * query-параметра `sort=` (P9.T2, переключатель "обратный порядок" в `LibraryScreen`):
     * вызывается заново при КАЖДОЙ загрузке страницы (в т.ч. [Paginator.refresh]), поэтому
     * тоггл во ViewModel меняет порядок без пересоздания самого `Paginator`.
     *
     * Элементы обогащаются прогрессом просмотра ([withWatchedPositions]) — сам `profile/list`
     * его не отдаёт.
     */
    fun listPaginator(
        status: ListStatus,
        sort: () -> Int = defaultSort,
    ): Paginator<Release> = Paginator { page -> myList(status, page, sort()).withWatchedPositions() }

    /** `null` — релиз не числится ни в одном списке. Прямой passthrough локальной истины, TTL не нужен. */
    fun observeListStatus(releaseId: ReleaseId): Flow<ListStatus?> = listMembershipStore.observeStatus(releaseId)

    fun observeMyList(
        status: ListStatus,
        page: Int,
    ): Flow<Cached<Paged<Release>>> {
        val key = CacheKeys.myList(status.apiValue, page)
        return cacheFirstFlow(
            local = hydratePagedIds(releaseListStore.observePage(key), releaseCacheStore),
            stampAt = { releaseListStore.fetchedAt(key) },
            policy = CachePolicy.CatalogListing,
            refresh = { persistPagedReleases(key, myList(status, page), stores, clock.now()) },
            clock = clock,
        )
    }

    /**
     * Добавляет релиз в список со статусом [status] — оптимистично локально, отправка на сервер
     * через [SyncQueueStore] (переживает офлайн, P4.T5).
     *
     * `[TODO: verify live]` (архитектурное допущение Фазы 6, не проверено живым запросом):
     * предполагаем, что статус в `profile/list/...` на сервере эксклюзивный, и повторный
     * `profile/list/add/{status}/{r_id}` с новым статусом сам заменяет старый — без явного
     * предварительного `removeFromList` со старым статусом. Если это окажется не так, при смене
     * статуса релиз останется числиться сразу в двух списках на сервере, и UI разойдётся с
     * официальным приложением. Сознательный трейдофф для MVP — один запрос вместо двух и без
     * гонки состояния между `remove`+`add`.
     */
    suspend fun addToList(
        status: ListStatus,
        releaseId: ReleaseId,
    ) {
        val now = clock.now()
        listMembershipStore.setStatus(releaseId, status, now)
        enqueue(
            kind = SyncOperationKind.LIST_SET_STATUS,
            entityKey = "list:$releaseId",
            releaseId = releaseId,
            statusApiValue = status.apiValue,
            now = now,
        )
        syncQueueWorker.drain()
    }

    /**
     * Читает текущий статус ДО его локального обнуления и кладёт в
     * [SyncOperation.statusApiValue] — не потому, что этот кейс задокументирован для
     * [SyncOperationKind.LIST_REMOVE] как основной (KDoc `SyncOperation.statusApiValue` описывает
     * его как поле [SyncOperationKind.LIST_SET_STATUS]), а потому что без этого
     * `SyncQueueWorker.resolveRemovalStatus` (см. его KDoc, пункт 2 — он допускает это ровно как
     * подстраховку) не смог бы узнать, какой статус удалять на сервере: сам он читает
     * `ListMembershipStore` уже ПОСЛЕ того, как строка ниже успела оптимистично записать туда
     * `null` — иначе `profile/list/delete/{status}/{r_id}` никогда бы не ушёл на сервер, а
     * `LIST_REMOVE` каждый раз тихо считался бы «уже выполненным».
     */
    suspend fun removeFromList(releaseId: ReleaseId) {
        val now = clock.now()
        val previousStatus = listMembershipStore.observeStatus(releaseId).first()
        listMembershipStore.setStatus(releaseId, null, now)
        enqueue(
            kind = SyncOperationKind.LIST_REMOVE,
            entityKey = "list:$releaseId",
            releaseId = releaseId,
            statusApiValue = previousStatus?.apiValue,
            now = now,
        )
        syncQueueWorker.drain()
    }

    // ---- Избранное ----------------------------------------------------------------------

    suspend fun favorites(
        page: Int,
        sort: Int = SORT_RECENTLY_ADDED,
    ): Paged<Release> = favoriteApi.favorites(page, sort = sort).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана избранного. [sort] — см. KDoc [listPaginator], тот же смысл. */
    fun favoritesPaginator(sort: () -> Int = defaultSort): Paginator<Release> =
        Paginator { page ->
            favorites(page, sort()).withWatchedPositions()
        }

    fun observeFavorite(releaseId: ReleaseId): Flow<Boolean> = listMembershipStore.observeFavorite(releaseId)

    fun observeFavorites(page: Int): Flow<Cached<Paged<Release>>> {
        val key = CacheKeys.favorites(page)
        return cacheFirstFlow(
            local = hydratePagedIds(releaseListStore.observePage(key), releaseCacheStore),
            stampAt = { releaseListStore.fetchedAt(key) },
            policy = CachePolicy.CatalogListing,
            refresh = { persistPagedReleases(key, favorites(page), stores, clock.now()) },
            clock = clock,
        )
    }

    suspend fun addFavorite(releaseId: ReleaseId) {
        setFavoriteAndEnqueue(releaseId, isFavorite = true)
    }

    suspend fun removeFavorite(releaseId: ReleaseId) {
        setFavoriteAndEnqueue(releaseId, isFavorite = false)
    }

    private suspend fun setFavoriteAndEnqueue(
        releaseId: ReleaseId,
        isFavorite: Boolean,
    ) {
        val now = clock.now()
        listMembershipStore.setFavorite(releaseId, isFavorite, now)
        enqueue(
            kind = SyncOperationKind.FAVORITE_SET,
            entityKey = "favorite:$releaseId",
            releaseId = releaseId,
            boolArg = isFavorite,
            now = now,
        )
        syncQueueWorker.drain()
    }

    // ---- История просмотра ---------------------------------------------------------------

    suspend fun history(page: Int): Paged<Release> = historyApi.history(page).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана истории просмотра. */
    fun historyPaginator(): Paginator<Release> = Paginator { page -> history(page) }

    // ---- Прогресс просмотра для списков (обогащение из истории) --------------------------

    private val watchedPositionsMutex = Mutex()
    private var watchedPositionsCache: Pair<Instant, Map<ReleaseId, Int>>? = null

    /**
     * Карта `releaseId → номер последней просмотренной серии`, собранная постранично из
     * `history/{page}`. Живая проверка 2026-09-18: `profile/list/all/{status}/{page}` отдаёт
     * `last_view_episode: null` по всем элементам (и с `extended_mode=1` тоже), других полей
     * прогресса в ответе нет — поэтому «N из M» во вкладках «Мои списки» достаётся только
     * обогащением из истории, где `last_view_episode` приходит объектом эпизода (см.
     * `LastViewEpisodeSerializer`). Кэш в памяти на [WATCHED_POSITIONS_TTL]: полная история —
     * десятки страниц, перечитывать её на каждую вкладку расточительно.
     */
    private suspend fun watchedPositions(): Map<ReleaseId, Int> {
        watchedPositionsCache?.let { (fetchedAt, cached) ->
            if (clock.now() - fetchedAt < WATCHED_POSITIONS_TTL) return cached
        }
        return watchedPositionsMutex.withLock {
            watchedPositionsCache?.let { (fetchedAt, cached) ->
                if (clock.now() - fetchedAt < WATCHED_POSITIONS_TTL) return@withLock cached
            }
            val positions = mutableMapOf<ReleaseId, Int>()
            var page = 0
            while (page < WATCHED_POSITIONS_MAX_PAGES) {
                val paged = history(page)
                // История идёт от свежих записей к старым — при дублях релиза первое
                // (свежее) значение оставляем, поэтому putIfAbsent, а не overwrite.
                paged.items.forEach { release ->
                    release.lastViewEpisode?.let { positions.putIfAbsent(release.id, it) }
                }
                if (!paged.hasNextPage) break
                page++
            }
            watchedPositionsCache = clock.now() to positions
            positions
        }
    }

    /**
     * Подставляет прогресс из [watchedPositions] в элементы страницы, у которых сервер не прислал
     * свой `lastViewEpisode`. Сбой загрузки истории не роняет сам список — возвращается страница
     * как есть (вчерашнее поведение «0 из N» лучше, чем ошибка вкладки).
     */
    private suspend fun Paged<Release>.withWatchedPositions(): Paged<Release> {
        // Сбой загрузки истории не роняет сам список — emptyMap даст страницу как есть.
        val positions = runCatching { watchedPositions() }.getOrDefault(emptyMap())
        return if (positions.isEmpty()) {
            this
        } else {
            copy(
                items =
                    items.map { release ->
                        if (release.lastViewEpisode != null) {
                            release
                        } else {
                            positions[release.id]?.let { release.copy(lastViewEpisode = it) } ?: release
                        }
                    },
            )
        }
    }

    fun observeHistory(page: Int): Flow<Cached<Paged<Release>>> {
        val key = CacheKeys.history(page)
        return cacheFirstFlow(
            local = hydratePagedIds(releaseListStore.observePage(key), releaseCacheStore),
            stampAt = { releaseListStore.fetchedAt(key) },
            policy = CachePolicy.CatalogListing,
            refresh = { persistPagedReleases(key, history(page), stores, clock.now()) },
            clock = clock,
        )
    }

    /**
     * История — единственная мутация без локального стора-зеркала (нет отдельного
     * "историйного" стора в S1-контракте, только очередь): читается она через [observeHistory]/
     * [history] напрямую из кэша листинга, без промежуточной оптимистичной локальной таблицы.
     */
    suspend fun addHistory(
        releaseId: ReleaseId,
        sourceId: Int,
        position: Int,
    ) {
        val now = clock.now()
        enqueue(
            kind = SyncOperationKind.HISTORY_ADD,
            entityKey = "history:$releaseId",
            releaseId = releaseId,
            sourceId = sourceId,
            position = position,
            now = now,
        )
        syncQueueWorker.drain()
    }

    suspend fun removeFromHistory(releaseId: ReleaseId) {
        val now = clock.now()
        enqueue(
            kind = SyncOperationKind.HISTORY_REMOVE,
            entityKey = "history:$releaseId",
            releaseId = releaseId,
            now = now,
        )
        syncQueueWorker.drain()
    }

    // ---- Общие хелперы --------------------------------------------------------------------

    @Suppress("LongParameterList")
    private suspend fun enqueue(
        kind: SyncOperationKind,
        entityKey: String,
        releaseId: ReleaseId,
        now: Instant,
        sourceId: Int? = null,
        position: Int? = null,
        statusApiValue: Int? = null,
        boolArg: Boolean? = null,
    ) {
        syncQueueStore.enqueue(
            SyncOperation(
                id = 0,
                kind = kind,
                entityKey = entityKey,
                releaseId = releaseId,
                sourceId = sourceId,
                position = position,
                statusApiValue = statusApiValue,
                boolArg = boolArg,
                createdAt = now,
                updatedAt = now,
                attemptCount = 0,
                nextAttemptAt = null,
                lastError = null,
            ),
        )
    }

    companion object {
        /**
         * `sort=1` — см. KDoc [ProfileListApi.myList]: эмпирически «сначала недавно добавленные»
         * (уже пофикшенный баг сортировки), дефолт [myList]/[favorites]/[listPaginator]/
         * [favoritesPaginator].
         */
        const val SORT_RECENTLY_ADDED = 1

        /** TTL in-memory кэша карты прогресса просмотра — см. [watchedPositions]. */
        private val WATCHED_POSITIONS_TTL = 5.minutes

        /** Предохранитель от бесконечного цикла при битой пагинации истории (25 × 100 = 2500 записей). */
        private const val WATCHED_POSITIONS_MAX_PAGES = 100

        /** `sort=0` — обратный порядок (P9.T2, переключатель «реверс» в `LibraryScreen`). */
        const val SORT_REVERSED = 0

        /**
         * Дефолт параметра `sort` у [listPaginator]/[favoritesPaginator] — короткое имя вместо
         * `{ SORT_RECENTLY_ADDED }` прямо в сигнатуре нужно не только для читаемости: инлайн-лямбда
         * там раздувает сигнатуру за [io.gitlab.arturbosch.detekt] `MaxLineLength` (ktlint сворачивает
         * короткий однопараметрический `fun ...(): X = Y { ... }` в одну строку независимо от её
         * итоговой длины, поэтому длину нужно контролировать на входе).
         */
        private val defaultSort: () -> Int = { SORT_RECENTLY_ADDED }
    }
}
