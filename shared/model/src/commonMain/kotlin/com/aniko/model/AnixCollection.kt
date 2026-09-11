package com.aniko.model

/**
 * Публичная коллекция (P16.T16, MVP) — `GET collection/all/{page}`. Названа `AnixCollection`, не
 * `Collection` — конфликт имени с `kotlin.collections.Collection`.
 *
 * MVP: только то, что нужно карточке списка (просмотр, без создания/лайка/приватных коллекций
 * своего аккаунта, без вложенного списка релизов — см. KDoc `CollectionDto` в `shared/data`).
 */
data class AnixCollection(
    val id: Long,
    val creator: CommentAuthor?,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val favoritesCount: Int,
    val commentCount: Long,
)
