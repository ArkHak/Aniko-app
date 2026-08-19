package com.aniko.model

/**
 * Одно уведомление из ленты `GET notification/all/{page}` (P10.T6).
 *
 * Названо `AppNotification`, а не `Notification`: последнее — имя платформенного класса
 * `android.app.Notification`, и в `androidMain` пришлось бы всюду писать полные имена.
 *
 * @param id серверный идентификатор. Единственное, по чему считается дифф «новое/уже виденное»
 * ([com.aniko.data.notification.selectUnseen]): монотонно растущий, в отличие от [timestamp],
 * который у разных типов уведомлений проставляется по-разному.
 * @param kind тип уведомления, см. [AppNotificationKind].
 * @param isNew серверный флаг «не прочитано» (`is_new`). НЕ используется для диффа — он
 * сбрасывается на `GET notification/read`, то есть зависит от действий пользователя в
 * официальном клиенте, а нам нужен признак «мы это уже показывали».
 * @param releaseId тайтл, к которому относится уведомление, если он есть в ответе — из него
 * строится deep link `aniko://release/{id}`.
 * @param releaseTitle русское название тайтла (`title_ru` в `ReleaseCompact`).
 * @param episodeName человекочитаемое имя серии (`"1 серия"` и т.п.) — только у [AppNotificationKind.EPISODE].
 * @param sourceName озвучка/источник новой серии — только у [AppNotificationKind.EPISODE].
 * @param profileLogin логин профиля-инициатора — только у [AppNotificationKind.FRIEND].
 */
data class AppNotification(
    val id: Long,
    val kind: AppNotificationKind,
    val timestamp: Long = 0,
    val isNew: Boolean = false,
    val releaseId: Int? = null,
    val releaseTitle: String? = null,
    val episodeName: String? = null,
    val sourceName: String? = null,
    val profileLogin: String? = null,
)

/**
 * Дискриминатор `type` из ответа сервера.
 *
 * Значения сверены с `@JsonSubTypes` на `ProfileNotification.java` в декомпиле
 * (`docs/api/jadx-out-21`): `friend`, `episode`, `releaseComment`, `collectionComment`,
 * `myCollection`, `article`, `articleComment`, `myArticle`, `relatedRelease`.
 *
 * [UNKNOWN] — обязательный элемент, а не перестраховка: в оригинальном клиенте у
 * `@JsonTypeInfo` стоит `defaultImpl = UnsupportedProfileNotification.class`, то есть сам Anixart
 * закладывается на появление новых типов у уже выпущенных клиентов. Уведомления неизвестного типа
 * мы показываем с обобщённым текстом, а не отбрасываем: пользователь всё равно должен узнать, что
 * в аккаунте что-то произошло.
 */
enum class AppNotificationKind {
    /** Вышла новая серия отслеживаемого тайтла — целевой сценарий P10.T6. */
    EPISODE,

    /** Вышел связанный релиз (сиквел/спин-офф) тайтла из списков пользователя. */
    RELATED_RELEASE,

    /** Заявка в друзья / принятие заявки. */
    FRIEND,

    /** Ответ на комментарий пользователя (к релизу, коллекции или статье). */
    COMMENT,

    /** Новая статья / комментарий к своей статье. */
    ARTICLE,

    UNKNOWN,
}

/**
 * Серверные тумблеры типов уведомлений (`GET profile/preference/notification/my`, P10.T6).
 *
 * Это НЕ локальная настройка приложения: флаги живут на бэкенде Anixart и управляют тем, что
 * вообще попадёт в ленту `notification/all` — то есть выключенный тумблер убирает уведомления и в
 * официальном клиенте тоже.
 *
 * Набор — ровно восемь `SwitchPreference` из `res/xml/preference_notifications.xml` декомпила,
 * сверенных с полями `NotificationPreferenceResponse.java` один к одному. Полей `friend` среди
 * них нет вообще (уведомления о друзьях не отключаются), хотя тип уведомления `friend`
 * существует, — см. журнал P10.T6 в `docs/REELWAVE_PLAN.md`.
 *
 * Три оставшихся пункта того же экрана (`profile_status_notification_preferences`,
 * `profile_type_notification_preferences`, `profile_release_type_notification_preferences`) —
 * не булевы тумблеры, а списки выбора (по статусам списков / по типам тайтлов / по конкретным
 * тайтлам) с собственными `POST .../edit`-телами и своей пагинацией; они в объём P10.T6 не входят.
 */
data class NotificationPreferences(
    val episodes: Boolean = false,
    val firstEpisode: Boolean = false,
    val relatedReleases: Boolean = false,
    val articles: Boolean = false,
    val comments: Boolean = false,
    val myCollectionComments: Boolean = false,
    val myArticleComments: Boolean = false,
    val reportProcess: Boolean = false,
)

/**
 * Какой именно тумблер переключает пользователь.
 *
 * Отдельный enum, а не девять методов репозитория: все девять эндпоинтов — GET без тела, сервер
 * сам инвертирует флаг (тот же приём, что `profile/preference/privacy/incognito/edit` в Фазе 7),
 * поэтому различаются они ровно одним — путём.
 */
enum class NotificationPreferenceToggle {
    EPISODES,
    FIRST_EPISODE,
    RELATED_RELEASES,
    ARTICLES,
    COMMENTS,
    MY_COLLECTION_COMMENTS,
    MY_ARTICLE_COMMENTS,
    REPORT_PROCESS,
}
