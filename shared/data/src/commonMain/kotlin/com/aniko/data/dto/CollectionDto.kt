package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET collection/all/{page}` — публичные коллекции (P16.T16, MVP: список/просмотр,
 * без создания/редактирования/лайка).
 *
 * Живьём подтверждено (Волна 2, `docs/REELWAVE_PLAN.md`): `collection/all/0`, `collection/1`,
 * `collection/1/releases/0`, `collection/all/profile/{id}/0` → реальные данные БЕЗ токена
 * (creator/постеры) — единственный публичный (не требующий авторизации) кусок decompiled
 * `CollectionApi.java` в этой волне задач. `collections(page, previousPage, where, sort, token)`
 * в decompile формально требует все параметры (примитивы, не nullable), но живой curl-проб
 * `collection/all/0` без `previous_page`/`where`/`sort` уже вернул 200 с реальными данными —
 * сервер принимает их отсутствие (видимо, есть server-side дефолты), поэтому здесь эти три
 * параметра не заводятся: минимальный проверенный набор.
 *
 * Поля сверены 1:1 с `database/entity/collection/Collection.java` (`@JsonProperty`, где имя JSON
 * расходится с camelCase): `favorites_count` (не `favorite_count`), `comment_count`,
 * `creation_date`. `releases`/`isPrivate`/`isFavorite`/`delete`/`lastUpdateDate` не нужны MVP-
 * списку (только просмотр карточки — не создание/лайк/приватные коллекции своего аккаунта).
 */
@Serializable
data class CollectionDto(
    val id: Long = 0,
    val creator: ProfileCompactDto? = null,
    val title: String = "",
    val description: String = "",
    val image: String = "",
    @SerialName("favorites_count") val favoritesCount: Int = 0,
    @SerialName("comment_count") val commentCount: Long = 0,
)
