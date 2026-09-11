package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET feed/latest/all/{page}` — общая публичная лента постов каналов/блогов (P16.T3, MVP).
 *
 * Живьём подтверждено только само существование маршрута (Волна 2, `docs/REELWAVE_PLAN.md`:
 * `feed/all/0`/`feed/latest`/`feed/latest/all/0` → `{"code":401}` без токена = маршрут есть, за
 * авторизацией). Форма успешного ответа — из декомпила (`network/api/FeedApi.java`:
 * `Observable<PageableResponse<Article>>`), полей `feed/latest`/детали статьи здесь нет вообще —
 * MVP: только список, без открытия отдельной статьи/постинга/голосования/репостов
 * (`docs/REELWAVE_PLAN.md`, отчёт задачи — решение «большой объём → MVP в v1.0.0, полный rich-
 * редактор/соцслой → v1.1.0»). `(не подтверждено вживую)` — то же ограничение, что у
 * `NotificationCountResponseDto`/`ProfileNotificationDto`: без реального аккаунта с токеном на
 * этапе разработки живой сэмпл снять не на чем, но `AnixTokenPlugin` дописывает токен реальной
 * сессии автоматически на устройстве — реальная проверка происходит через саму фичу в рантайме,
 * не curl-пробой этого чата.
 *
 * **Полиморфные блоки контента статьи намеренно СПЛЮЩЕНЫ** — тот же приём и то же обоснование,
 * что у [ProfileNotificationDto] (см. её KDoc): на сервере `ArticleBlock` — полиморфный тип
 * (`@JsonTypeInfo(property = "type")`, 7 подтипов: paragraph/header/quote/delimiter/list/media/
 * embed, см. `articles/models/ArticleBlock.java`+`ArticleBlock.java`'s `@JsonSubTypes`). MVP-превью
 * ленты нужен только plain-текст — извлекается из `type in {"paragraph","header"}` блоков
 * ([com.aniko.data.mapper.toPreviewText]), остальные типы (картинки/embed-видео/списки/цитаты)
 * для превью не нужны и просто игнорируются `ignoreUnknownKeys` (уже включён в `AnixJson`,
 * `AnixHttpClient.kt`) — не отдельная полиморфная иерархия ради untouched-полей.
 */
@Serializable
data class ChannelCompactDto(
    val id: Long = 0,
    val title: String? = null,
    val avatar: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

/** Данные блока контента статьи — только то, что нужно MVP-превью (см. KDoc файла): `text` у
 *  paragraph/header-блоков. Остальные поля других типов блоков (картинки, embed-ссылки, уровень
 *  заголовка и т.п.) не нужны превью и игнорируются `ignoreUnknownKeys`. */
@Serializable
data class ArticleBlockDataDto(
    val text: String? = null,
)

/** Один блок EditorJS-контента статьи — дискриминатор `type` (см. KDoc файла): `"paragraph"`/
 *  `"header"` содержат `data.text`, остальные типы (`quote`/`delimiter`/`list`/`media`/`embed`)
 *  для MVP-превью пропускаются. */
@Serializable
data class ArticleBlockDto(
    val type: String? = null,
    val data: ArticleBlockDataDto? = null,
)

/** `ArticlePayload` — только список блоков (`blocks`), `time`/`version`/`blockCount` из
 *  декомпила не нужны MVP-превью. */
@Serializable
data class ArticlePayloadDto(
    val blocks: List<ArticleBlockDto> = emptyList(),
)

/**
 * `Article` (см. KDoc файла) — MVP-подмножество полей декомпилированного `Article.java`: только
 * то, что нужно карточке ленты (автор/канал/превью-текст/счётчики/время). Без `repostArticle`
 * (репост показываем как обычный пост, без вложенной карточки — MVP), `popularComment` (нужен
 * только детальному экрану статьи, которого нет в MVP), `vote` (голосование — CUT, см. KDoc
 * файла).
 */
@Serializable
data class ArticleDto(
    val id: Long = 0,
    val channel: ChannelCompactDto? = null,
    val author: ProfileCompactDto? = null,
    val payload: ArticlePayloadDto? = null,
    @SerialName("creation_date") val creationDate: Long = 0,
    @SerialName("comment_count") val commentCount: Long = 0,
    @SerialName("repost_count") val repostCount: Long = 0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("is_pinned") val isPinned: Boolean = false,
)
