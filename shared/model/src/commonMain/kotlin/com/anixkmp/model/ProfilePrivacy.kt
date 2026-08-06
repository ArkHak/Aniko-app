package com.anixkmp.model

/**
 * Видимость раздела профиля для privacy-настроек (`profile/preference/privacy/.../edit`).
 *
 * Числовые значения — те же, что использует Anixart API в теле `PrivacyEditRequest.permission`
 * (см. `docs/api/jadx-out-21/sources/.../network/request/profile/PrivacyEditRequest.java`) и в
 * ответе `profile/preference/my` (`privacy_stats`/`privacy_counts`/`privacy_social`).
 * `[TODO: verify live]` — расшифровка 0/1/2 взята по аналогии с [ListStatus] (enum поверх Int из
 * API), сам список значений в decompiled-источниках не документирован текстом — предположение
 * «все/только друзья/только я» стандартно для privacy-переключателей такого вида в этом клиенте.
 */
enum class PrivacyVisibility {
    EVERYONE,
    FRIENDS_ONLY,
    ONLY_ME,
    ;

    fun toApiValue(): Int = when (this) {
        EVERYONE -> 0
        FRIENDS_ONLY -> 1
        ONLY_ME -> 2
    }

    companion object {
        fun fromApiValue(value: Int): PrivacyVisibility = when (value) {
            1 -> FRIENDS_ONLY
            2 -> ONLY_ME
            else -> EVERYONE
        }
    }
}

/** Видимость для приёма заявок в друзья (`privacy_friend_requests`) — только два состояния. */
enum class FriendRequestVisibility {
    EVERYONE,
    NOBODY,
    ;

    fun toApiValue(): Int = when (this) {
        EVERYONE -> 0
        NOBODY -> 1
    }

    companion object {
        fun fromApiValue(value: Int): FriendRequestVisibility = when (value) {
            1 -> NOBODY
            else -> EVERYONE
        }
    }
}

/** Снимок privacy-настроек текущего пользователя (`profile/preference/my`). */
data class ProfilePrivacy(
    val stats: PrivacyVisibility,
    val counts: PrivacyVisibility,
    val social: PrivacyVisibility,
    val friendRequests: FriendRequestVisibility,
    val isIncognito: Boolean,
)
