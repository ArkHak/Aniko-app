package com.aniko.app.notification

import com.aniko.data.locale.LocaleStore
import com.aniko.data.notification.LocalNotification
import com.aniko.data.notification.NotificationContentFactory
import com.aniko.model.AppNotification
import com.aniko.model.AppNotificationKind
import com.aniko.ui.i18n.Strings
import com.aniko.ui.i18n.appStringsFor

/**
 * Локализованные тексты локальных уведомлений (P10.T6).
 *
 * Живёт в `composeApp`, а не в `shared/data`, потому что это единственный модуль, который видит
 * одновременно и данные (`AppNotification`), и переводы (`shared/ui`) — сам `shared/data` от
 * `shared/ui` не зависит и не должен, см. KDoc [NotificationContentFactory].
 *
 * Язык берётся из [LocaleStore] на КАЖДЫЙ вызов, а не один раз в конструкторе: экземпляр —
 * синглтон Koin, живущий столько же, сколько процесс, а язык пользователь может сменить в любой
 * момент. `appStringsFor(null)` при этом честно уходит в системную локаль — та же логика, что у
 * `ProvideAppStrings` для UI.
 *
 * Deep link строится ровно по схеме P10.T7 (`aniko://release/{id}`), а не через свой формат:
 * разбирает его тот же `parseDeepLink`, и второго источника правды о формате ссылки не появляется.
 */
class AppNotificationContentFactory(
    private val localeStore: LocaleStore,
) : NotificationContentFactory {
    override fun create(notification: AppNotification): LocalNotification? {
        val strings = appStringsFor(localeStore.languageTag.value)
        val (title, body) = titleAndBody(notification, strings)
        return LocalNotification(
            id = notification.id,
            title = title,
            body = body,
            deepLink = notification.releaseId?.let { "$DEEP_LINK_RELEASE_PREFIX$it" },
        )
    }

    /**
     * Ни одна ветка не возвращает `null`: даже про уведомление неизвестного типа
     * ([AppNotificationKind.UNKNOWN]) пользователю честнее сказать обобщённым текстом, чем молча
     * проглотить — иначе он узнает о событии только открыв официальный клиент. Контракт
     * [NotificationContentFactory.create] допускает `null`, но здесь он не нужен.
     */
    private fun titleAndBody(
        notification: AppNotification,
        strings: Strings,
    ): Pair<String, String> =
        when (notification.kind) {
            AppNotificationKind.EPISODE ->
                strings.notificationNewEpisodeTitle to episodeBody(notification, strings)

            AppNotificationKind.RELATED_RELEASE ->
                strings.notificationRelatedReleaseTitle to
                    (
                        notification.releaseTitle?.let(strings.notificationRelatedReleaseBody)
                            ?: strings.notificationGenericBody
                    )

            AppNotificationKind.FRIEND ->
                strings.notificationFriendTitle to
                    (
                        notification.profileLogin?.let(strings.notificationFriendBody)
                            ?: strings.notificationGenericBody
                    )

            AppNotificationKind.COMMENT ->
                strings.notificationCommentTitle to strings.notificationCommentBody

            AppNotificationKind.ARTICLE ->
                strings.notificationArticleTitle to strings.notificationArticleBody

            AppNotificationKind.UNKNOWN ->
                strings.notificationGenericTitle to strings.notificationGenericBody
        }

    /**
     * Название тайтла — обязательная часть текста: без него «Новая серия» ничего не сообщает.
     * Имя серии необязательно (сервер присылает его не всегда), поэтому под этот случай отдельный
     * ключ, а не склейка с пустой строкой и висящим разделителем.
     */
    private fun episodeBody(
        notification: AppNotification,
        strings: Strings,
    ): String {
        val title = notification.releaseTitle ?: return strings.notificationGenericBody
        return notification.episodeName?.let { episode -> strings.notificationNewEpisodeBody(title, episode) }
            ?: strings.notificationNewEpisodeBodyNoEpisode(title)
    }

    private companion object {
        /** Совпадает со схемой `DeepLink.kt` (P10.T7) — единственный формат ссылок приложения. */
        const val DEEP_LINK_RELEASE_PREFIX = "aniko://release/"
    }
}
