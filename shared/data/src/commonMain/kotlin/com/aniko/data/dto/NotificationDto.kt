package com.aniko.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET notification/count` → `NotificationCountResponse` (P10.T6).
 *
 * Живая проверка 2026-08-19: `GET https://api-s.anixsekai.com/notification/count?token=<invalid>`
 * отвечает `{"code":401}` — эндпоинт существует и отвечает в общей обёртке `code`, но снять
 * реальную форму успешного ответа не на чем: рабочего токена авторизованного аккаунта в проекте
 * нет (в `docs/api/samples/` лежат только неавторизованные выдачи). Поэтому поля взяты из
 * декомпила (`network/response/notifications/NotificationCountResponse.java`: единственное
 * поле `long count` без `@JsonProperty`, то есть имя в JSON — ровно `count`), и это
 * (не подтверждено вживую) до появления живого сэмпла с токеном.
 *
 * `count` — `Long`, как в декомпиле; сужать до `Int` смысла нет, а расхождение типа с сервером
 * стоило бы падения парсинга.
 */
@Serializable
data class NotificationCountResponseDto(
    override val code: Int = 0,
    val count: Long = 0,
) : ApiCodeAware

/**
 * Элемент ленты `GET notification/all/{page}` — **намеренно плоский** DTO вместо полиморфной
 * иерархии.
 *
 * На сервере это полиморфный тип: `ProfileNotification` с `@JsonTypeInfo(property = "type")` и
 * девятью `@JsonSubTypes` (`friend`, `episode`, `releaseComment`, `collectionComment`,
 * `myCollection`, `article`, `articleComment`, `myArticle`, `relatedRelease`). Воспроизводить это
 * `sealed`-иерархией + `SerializersModule.polymorphic` в kotlinx.serialization можно, но здесь
 * это чистый проигрыш:
 * - подтипы не пересекаются по полям, а **дополняют** общую базу (`id`/`timestamp`/`is_new`)
 *   ровно одним-двумя своими полями — то есть плоская запись с nullable-полями теряет ровно
 *   ноль информации;
 * - полиморфный декодер требует зарегистрировать `defaultDeserializer` под неизвестный `type`,
 *   иначе новый тип уведомления на сервере уронит парсинг всей страницы. Сам Anixart закладывается
 *   на такое (`defaultImpl = UnsupportedProfileNotification.class`), и повторять этот механизм
 *   через кастомный `SerializersModule` ради того же результата — лишний слой;
 * - `AnixJson { ignoreUnknownKeys = true }` (`AnixHttpClient.kt`) уже даёт нужную устойчивость:
 *   поля незнакомых подтипов просто игнорируются, а `type` остаётся строкой и маппится в
 *   [com.aniko.model.AppNotificationKind.UNKNOWN].
 *
 * Поля сверены с декомпилом (пакет `database/entity/notification`): база — `id`, `timestamp`,
 * `is_new`, `is_pushed`; `episode` (`ProfileEpisodeNotification`), `release`
 * (`ProfileRelatedReleaseNotification`), `by_profile` (`ProfileFriendNotification`),
 * `comment`/`parent_comment` (`ProfileCommentNotification`). (не подтверждено вживую) — как и
 * [NotificationCountResponseDto], без токена не проверить.
 */
@Serializable
data class ProfileNotificationDto(
    val id: Long = 0,
    val type: String? = null,
    val timestamp: Long = 0,
    @SerialName("is_new") val isNew: Boolean = false,
    @SerialName("is_pushed") val isPushed: Boolean = false,
    val episode: EpisodeCompactDto? = null,
    val release: ReleaseCompactDto? = null,
    @SerialName("by_profile") val byProfile: NotificationProfileDto? = null,
    val comment: NotificationCommentDto? = null,
)

/** `EpisodeCompact` — вложен в уведомление типа `episode`. */
@Serializable
data class EpisodeCompactDto(
    val name: String? = null,
    val release: ReleaseCompactDto? = null,
    val source: SourceCompactDto? = null,
)

/**
 * `ReleaseCompact` — урезанный релиз внутри уведомления: только `id`, `image`, `title_ru`.
 *
 * Отдельный тип, а не переиспользование [ReleaseDto]: у полноразмерного релиза десятки
 * обязательных для UI полей, а тут их физически нет, и подсовывать пустой [ReleaseDto] значило
 * бы соврать вызывающему о полноте данных.
 */
@Serializable
data class ReleaseCompactDto(
    val id: Long = 0,
    val image: String? = null,
    @SerialName("title_ru") val titleRu: String? = null,
)

/** `SourceCompact` — озвучка/источник новой серии. */
@Serializable
data class SourceCompactDto(
    val name: String? = null,
)

/** `ProfileSlim` — профиль-инициатор уведомления (заявка в друзья, автор комментария). */
@Serializable
data class NotificationProfileDto(
    val id: Long = 0,
    val login: String? = null,
)

/** `CommentCompact` — комментарий, на который ответили. Из него нужен только релиз для deep link. */
@Serializable
data class NotificationCommentDto(
    val id: Long = 0,
    val message: String? = null,
    val release: ReleaseCompactDto? = null,
)

/**
 * `GET profile/preference/notification/my` → `NotificationPreferenceResponse` (P10.T6).
 *
 * Восемь булевых полей — ровно те, что в декомпиле привязаны к `SwitchPreference` в
 * `res/xml/preference_notifications.xml`; имена сверены с `@JsonProperty` в
 * `network/response/preferences/NotificationPreferenceResponse.java`.
 *
 * Ещё три поля того же ответа сюда не заведены и игнорируются `ignoreUnknownKeys`:
 * `is_release_type_notifications_enabled`, `profileStatusNotificationPreferences`,
 * `profileTypeNotificationPreferences` — они обслуживают списки выбора (по статусам/типам/
 * конкретным тайтлам), а не тумблеры, и вне объёма P10.T6 (см. KDoc
 * [com.aniko.model.NotificationPreferences]).
 */
@Serializable
data class NotificationPreferenceResponseDto(
    override val code: Int = 0,
    @SerialName("is_episode_notifications_enabled") val episodes: Boolean = false,
    @SerialName("is_first_episode_notification_enabled") val firstEpisode: Boolean = false,
    @SerialName("is_related_release_notifications_enabled") val relatedReleases: Boolean = false,
    @SerialName("is_article_notifications_enabled") val articles: Boolean = false,
    @SerialName("is_comment_notifications_enabled") val comments: Boolean = false,
    @SerialName("is_my_collection_comment_notifications_enabled") val myCollectionComments: Boolean = false,
    @SerialName("is_my_article_comment_notifications_enabled") val myArticleComments: Boolean = false,
    @SerialName("is_report_process_notifications_enabled") val reportProcess: Boolean = false,
) : ApiCodeAware
