package com.anixkmp.data.repository

import com.anixkmp.data.api.ProfileApi
import com.anixkmp.data.api.ProfilePreferenceApi
import com.anixkmp.data.session.FakeSecureTokenStorage
import com.anixkmp.data.session.SessionStore
import com.anixkmp.model.AnixError
import com.anixkmp.model.FriendRequestVisibility
import com.anixkmp.model.PrivacyVisibility
import com.anixkmp.network.AnixJson
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Happy-path тесты `ProfileRepository` (Фаза 7) поверх `MockEngine` — по образцу
 * `LibraryRepositoryTest`. `SessionStore` создаётся с предустановленным `profileId` через
 * `save(token, profileId)` (см. `SessionStoreTest`), чтобы `myProfile()` мог его прочитать
 * синхронно из `Settings` без похода в сеть за токеном.
 */
class ProfileRepositoryTest {

    private val sampleProfileJson = """
        {
            "id": 42,
            "login": "test-user",
            "avatar": "avatars/42.png",
            "status": "Смотрю аниме",
            "is_sponsor": true,
            "sponsorshipExpires": 1999999999,
            "is_banned": false,
            "is_perm_banned": false,
            "ban_reason": null,
            "ban_expires": null,
            "privilege_level": 0,
            "rating_score": 120,
            "badge_name": "VIP",
            "badge_url": "badges/vip.png",
            "watching_count": 3,
            "plan_count": 5,
            "completed_count": 10,
            "hold_on_count": 1,
            "dropped_count": 2,
            "favorite_count": 7,
            "comment_count": 15,
            "collection_count": 4,
            "video_count": 0,
            "friend_count": 9,
            "watched_episode_count": 321,
            "watched_time": 123456,
            "register_date": 1600000000,
            "last_activity_time": 1700000000,
            "is_online": true,
            "is_verified": false
        }
    """.trimIndent()

    private fun profileResponse(code: Int = 0): String = """
        {
            "code": $code,
            "profile": $sampleProfileJson
        }
    """.trimIndent()

    private fun preferenceResponse(code: Int = 0): String = """
        {
            "code": $code,
            "privacy_stats": 1,
            "privacy_counts": 2,
            "privacy_social": 0,
            "privacy_friend_requests": 1,
            "is_incognito": true
        }
    """.trimIndent()

    private fun simpleResponse(code: Int = 0): String = """{"code": $code}"""

    private fun sessionStore(profileId: Long? = 42L): SessionStore {
        val store = SessionStore(settings = MapSettings(), secureStorage = FakeSecureTokenStorage())
        if (profileId != null) {
            // save() выставляет и токен, и profileId — токен здесь роли не играет, т.к.
            // AnixTokenPlugin в тестовом HttpClient (без него) не участвует.
            kotlinx.coroutines.runBlocking { store.save(token = "test-token", profileId = profileId) }
        }
        return store
    }

    private fun repository(
        expectedPath: String,
        responseBody: String,
        profileId: Long? = 42L,
    ): ProfileRepository {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            check(path == expectedPath) { "Unexpected path: $path, expected: $expectedPath" }
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(AnixJson) }
        }
        return ProfileRepository(
            profileApi = ProfileApi(client = httpClient),
            profilePreferenceApi = ProfilePreferenceApi(client = httpClient),
            sessionStore = sessionStore(profileId),
        )
    }

    // ---- myProfile ------------------------------------------------------------------------

    @Test
    fun myProfile_happyPath_returnsMappedProfile() = runTest {
        val repository = repository("/profile/42", profileResponse())

        val profile = repository.myProfile()

        assertEquals(42L, profile.id)
        assertEquals("test-user", profile.login)
        assertTrue(profile.avatarUrl.orEmpty().endsWith("avatars/42.png"))
        assertTrue(profile.isSponsor)
        assertEquals(120, profile.ratingScore)
        assertEquals(321, profile.watchedEpisodeCount)
        assertTrue(profile.isOnline)
    }

    @Test
    fun myProfile_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/42", profileResponse(code = 9))

        assertFailsWith<AnixError.Api> {
            repository.myProfile()
        }
    }

    @Test
    fun myProfile_noProfileId_throwsUnauthorized_withoutNetworkCall() = runTest {
        var requested = false
        val mockEngine = MockEngine { request ->
            requested = true
            respond(
                content = profileResponse(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(AnixJson) }
        }
        val repository = ProfileRepository(
            profileApi = ProfileApi(client = httpClient),
            profilePreferenceApi = ProfilePreferenceApi(client = httpClient),
            sessionStore = sessionStore(profileId = null),
        )

        assertFailsWith<AnixError.Unauthorized> {
            repository.myProfile()
        }
        assertFalse(requested, "HTTP-запрос не должен был случиться без profileId")
    }

    // ---- privacyPreferences -----------------------------------------------------------------

    @Test
    fun privacyPreferences_happyPath_returnsMappedPrivacy() = runTest {
        val repository = repository("/profile/preference/my", preferenceResponse())

        val privacy = repository.privacyPreferences()

        assertEquals(PrivacyVisibility.FRIENDS_ONLY, privacy.stats)
        assertEquals(PrivacyVisibility.ONLY_ME, privacy.counts)
        assertEquals(PrivacyVisibility.EVERYONE, privacy.social)
        assertEquals(FriendRequestVisibility.NOBODY, privacy.friendRequests)
        assertTrue(privacy.isIncognito)
    }

    @Test
    fun privacyPreferences_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/preference/my", preferenceResponse(code = 3))

        assertFailsWith<AnixError.Api> {
            repository.privacyPreferences()
        }
    }

    // ---- updatePrivacy* / toggleIncognito -----------------------------------------------------

    @Test
    fun updatePrivacyStats_happyPath_callsEditEndpoint() = runTest {
        val repository = repository("/profile/preference/privacy/stats/edit", simpleResponse())

        repository.updatePrivacyStats(PrivacyVisibility.FRIENDS_ONLY)
    }

    @Test
    fun updatePrivacyStats_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/preference/privacy/stats/edit", simpleResponse(code = 5))

        assertFailsWith<AnixError.Api> {
            repository.updatePrivacyStats(PrivacyVisibility.FRIENDS_ONLY)
        }
    }

    @Test
    fun updatePrivacyCounts_happyPath_callsEditEndpoint() = runTest {
        val repository = repository("/profile/preference/privacy/counts/edit", simpleResponse())

        repository.updatePrivacyCounts(PrivacyVisibility.ONLY_ME)
    }

    @Test
    fun updatePrivacyCounts_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/preference/privacy/counts/edit", simpleResponse(code = 5))

        assertFailsWith<AnixError.Api> {
            repository.updatePrivacyCounts(PrivacyVisibility.ONLY_ME)
        }
    }

    @Test
    fun updatePrivacySocial_happyPath_callsEditEndpoint() = runTest {
        val repository = repository("/profile/preference/privacy/social/edit", simpleResponse())

        repository.updatePrivacySocial(PrivacyVisibility.EVERYONE)
    }

    @Test
    fun updatePrivacySocial_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/preference/privacy/social/edit", simpleResponse(code = 5))

        assertFailsWith<AnixError.Api> {
            repository.updatePrivacySocial(PrivacyVisibility.EVERYONE)
        }
    }

    @Test
    fun updatePrivacyFriendRequests_happyPath_callsEditEndpoint() = runTest {
        val repository = repository("/profile/preference/privacy/friendRequests/edit", simpleResponse())

        repository.updatePrivacyFriendRequests(FriendRequestVisibility.NOBODY)
    }

    @Test
    fun updatePrivacyFriendRequests_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/preference/privacy/friendRequests/edit", simpleResponse(code = 5))

        assertFailsWith<AnixError.Api> {
            repository.updatePrivacyFriendRequests(FriendRequestVisibility.NOBODY)
        }
    }

    @Test
    fun toggleIncognito_happyPath_callsIncognitoEndpoint() = runTest {
        val repository = repository("/profile/preference/privacy/incognito/edit", simpleResponse())

        repository.toggleIncognito()
    }

    @Test
    fun toggleIncognito_nonZeroCode_throwsAnixErrorApi() = runTest {
        val repository = repository("/profile/preference/privacy/incognito/edit", simpleResponse(code = 5))

        assertFailsWith<AnixError.Api> {
            repository.toggleIncognito()
        }
    }
}
