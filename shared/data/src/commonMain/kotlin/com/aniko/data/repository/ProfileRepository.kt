package com.aniko.data.repository

import com.aniko.data.api.ProfileApi
import com.aniko.data.api.ProfilePreferenceApi
import com.aniko.data.mapper.toDomain
import com.aniko.data.session.SessionStore
import com.aniko.model.Achievement
import com.aniko.model.AnixError
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy

/**
 * Фаза 7 — «Профиль» (MVP): просмотр своего профиля + privacy-настройки.
 *
 * Как и [LibraryRepository], токен в запросы не передаётся явно — `AnixTokenPlugin` дописывает
 * `?token=` сам. `profileId` для [myProfile] берётся синхронно из [SessionStore] (см. его
 * `profileId()` — читает `Settings`, без I/O).
 */
class ProfileRepository(
    private val profileApi: ProfileApi,
    private val profilePreferenceApi: ProfilePreferenceApi,
    private val sessionStore: SessionStore,
) {
    /** Профиль текущего пользователя. Бросает [AnixError.Unauthorized], если нет активной сессии. */
    suspend fun myProfile(): ProfileDetails {
        val profileId = sessionStore.profileId() ?: throw AnixError.Unauthorized()
        return profileApi.profile(profileId).profile?.toDomain() ?: throw AnixError.Parsing()
    }

    /** Текущие privacy-настройки (`profile/preference/my`). */
    suspend fun privacyPreferences(): ProfilePrivacy = profilePreferenceApi.my().toDomain()

    suspend fun updatePrivacyStats(value: PrivacyVisibility) {
        profilePreferenceApi.privacyStatsEdit(value.toApiValue())
    }

    suspend fun updatePrivacyCounts(value: PrivacyVisibility) {
        profilePreferenceApi.privacyCountsEdit(value.toApiValue())
    }

    suspend fun updatePrivacySocial(value: PrivacyVisibility) {
        profilePreferenceApi.privacySocialEdit(value.toApiValue())
    }

    suspend fun updatePrivacyFriendRequests(value: FriendRequestVisibility) {
        profilePreferenceApi.privacyFriendRequestsEdit(value.toApiValue())
    }

    /** Переключает инкогнито. Локального состояния здесь нет — это забота ViewModel. */
    suspend fun toggleIncognito() {
        profilePreferenceApi.privacyIncognitoEdit()
    }

    /**
     * Уже полученные пользователем значки (`profile/preference/badge/all/{page}`). Экран профиля
     * грузит только первую страницу (0) — секция небольшая, отдельной пагинации в v1 UI нет.
     */
    suspend fun achievements(page: Int = 0): List<Achievement> {
        val badges = profilePreferenceApi.badges(page)
        return badges.content.map { it.toDomain() }
    }
}
