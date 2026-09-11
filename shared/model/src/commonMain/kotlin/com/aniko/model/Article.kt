package com.aniko.model

/**
 * Пост ленты (P16.T3, MVP) — `GET feed/latest/all/{page}`. Только то, что нужно карточке списка:
 * без вложенного репоста, голосования, popular-комментария и полного rich-контента (см. KDoc
 * `ArticleDto` в `shared/data`).
 *
 * [previewText] — plain-текст, извлечённый из paragraph/header-блоков EditorJS-контента (HTML-
 * теги внутри текста сняты), обрезан до разумной длины карточки. Пустая строка, если пост состоит
 * только из медиа/embed-блоков (без текстового содержимого) — карточка тогда просто не покажет
 * превью-строку, не будет пустого места с многоточием.
 */
data class Article(
    val id: Long,
    val channel: ArticleChannel?,
    val author: CommentAuthor?,
    val previewText: String,
    val creationDateSeconds: Long,
    val commentCount: Long,
    val repostCount: Long,
    val voteCount: Int,
    val isPinned: Boolean,
)

/** Канал/блог, опубликовавший пост — короткая форма (не полный `Channel` с описанием/счётчиками
 *  подписчиков, MVP их не показывает). */
data class ArticleChannel(
    val id: Long,
    val title: String,
    val avatarUrl: String?,
    val isVerified: Boolean,
)
