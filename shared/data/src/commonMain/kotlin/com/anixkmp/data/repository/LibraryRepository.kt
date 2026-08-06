package com.anixkmp.data.repository

import com.anixkmp.data.api.FavoriteApi
import com.anixkmp.data.api.HistoryApi
import com.anixkmp.data.api.ProfileListApi
import com.anixkmp.data.mapper.toDomain
import com.anixkmp.data.paging.Paginator
import com.anixkmp.model.ListStatus
import com.anixkmp.model.Paged
import com.anixkmp.model.Release

/**
 * Фаза 6 — «Списки и синхронизация»: списки по статусу, избранное и история просмотра.
 *
 * Токен в запросы не передаётся явно — `AnixTokenPlugin` (см. `:shared:network`) дописывает
 * `?token=` в каждый исходящий запрос сам, читая его из `TokenProvider`, поэтому здесь (как и в
 * `ReleaseRepository`/`EpisodeRepository`) о нём заботиться не нужно.
 */
class LibraryRepository(
    private val profileListApi: ProfileListApi,
    private val favoriteApi: FavoriteApi,
    private val historyApi: HistoryApi,
) {

    // ---- Списки по статусу ------------------------------------------------------------

    suspend fun myList(status: ListStatus, page: Int): Paged<Release> =
        profileListApi.myList(status, page).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана списка по статусу. */
    fun listPaginator(status: ListStatus): Paginator<Release> = Paginator { page -> myList(status, page) }

    /**
     * Добавляет релиз в список со статусом [status].
     *
     * `[TODO: verify live]` (архитектурное допущение Фазы 6, не проверено живым запросом):
     * предполагаем, что статус в `profile/list/...` на сервере эксклюзивный, и повторный
     * `profile/list/add/{status}/{r_id}` с новым статусом сам заменяет старый — без явного
     * предварительного `removeFromList` со старым статусом. Если это окажется не так, при смене
     * статуса релиз останется числиться сразу в двух списках на сервере, и UI разойдётся с
     * официальным приложением. Сознательный трейдофф для MVP — один запрос вместо двух и без
     * гонки состояния между `remove`+`add`.
     */
    suspend fun addToList(status: ListStatus, releaseId: Int) {
        profileListApi.addToList(status, releaseId)
    }

    suspend fun removeFromList(status: ListStatus, releaseId: Int) {
        profileListApi.removeFromList(status, releaseId)
    }

    // ---- Избранное ----------------------------------------------------------------------

    suspend fun favorites(page: Int): Paged<Release> =
        favoriteApi.favorites(page).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана избранного. */
    fun favoritesPaginator(): Paginator<Release> = Paginator { page -> favorites(page) }

    suspend fun addFavorite(releaseId: Int) {
        favoriteApi.addFavorite(releaseId)
    }

    suspend fun removeFavorite(releaseId: Int) {
        favoriteApi.removeFavorite(releaseId)
    }

    // ---- История просмотра ---------------------------------------------------------------

    suspend fun history(page: Int): Paged<Release> =
        historyApi.history(page).toDomain { it.toDomain() }

    /** Готовый пагинатор для экрана истории просмотра. */
    fun historyPaginator(): Paginator<Release> = Paginator { page -> history(page) }

    suspend fun addHistory(releaseId: Int, sourceId: Int, position: Int) {
        historyApi.add(releaseId, sourceId, position)
    }

    suspend fun removeFromHistory(releaseId: Int) {
        historyApi.delete(releaseId)
    }
}
