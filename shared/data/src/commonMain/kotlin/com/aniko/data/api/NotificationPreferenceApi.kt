package com.aniko.data.api

import com.aniko.data.dto.NotificationPreferenceResponseDto
import com.aniko.data.dto.SimpleResponseDto
import com.aniko.model.NotificationPreferenceToggle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * `NotificationPreferenceApi` — серверные тумблеры типов уведомлений (P10.T6).
 *
 * Пути сверены построчно с decompiled `network/api/NotificationPreferenceApi.java`. Все
 * тумблеры — **`GET` без тела**: сервер сам инвертирует текущее значение флага, клиент не
 * передаёт желаемое состояние. Тот же приём уже встречался в
 * [ProfilePreferenceApi.privacyIncognitoEdit] (Фаза 7), так что это не догадка, а подтверждённая
 * конвенция этого API.
 *
 * Практическое следствие инвертирующей семантики: клиент обязан знать актуальное состояние ДО
 * переключения, иначе тумблер в UI разъедется с сервером. Поэтому [my] — единственный источник
 * правды, и [NotificationPreferenceRepository][com.aniko.data.repository.NotificationPreferenceRepository]
 * перечитывает его после каждого переключения.
 *
 * `POST`-эндпоинты того же интерфейса (`.../status/edit`, `.../type/edit`,
 * `.../release/type/edit`) и постраничный `.../release/all/{page}` не реализованы — они
 * обслуживают списки выбора, а не тумблеры, см. KDoc [com.aniko.model.NotificationPreferences].
 */
class NotificationPreferenceApi(
    private val client: HttpClient,
) {
    /** `GET profile/preference/notification/my` — текущее состояние всех тумблеров. */
    suspend fun my(): NotificationPreferenceResponseDto =
        apiCall {
            client.get("profile/preference/notification/my").body<NotificationPreferenceResponseDto>().requireOk()
        }

    /** Инвертирует один тумблер. Путь выбирается по [toggle], см. [pathOf]. */
    suspend fun toggle(toggle: NotificationPreferenceToggle): SimpleResponseDto =
        apiCall {
            client.get(pathOf(toggle)).body<SimpleResponseDto>().requireOk()
        }

    private fun pathOf(toggle: NotificationPreferenceToggle): String =
        when (toggle) {
            NotificationPreferenceToggle.EPISODES -> "profile/preference/notification/episode/edit"
            NotificationPreferenceToggle.FIRST_EPISODE -> "profile/preference/notification/episode/first/edit"
            NotificationPreferenceToggle.RELATED_RELEASES -> "profile/preference/notification/related/release/edit"
            NotificationPreferenceToggle.ARTICLES -> "profile/preference/notification/article/edit"
            NotificationPreferenceToggle.COMMENTS -> "profile/preference/notification/comment/edit"
            NotificationPreferenceToggle.MY_COLLECTION_COMMENTS ->
                "profile/preference/notification/my/collection/comment/edit"
            NotificationPreferenceToggle.MY_ARTICLE_COMMENTS ->
                "profile/preference/notification/my/article/comment/edit"
            NotificationPreferenceToggle.REPORT_PROCESS -> "profile/preference/notification/report/process/edit"
        }
}
