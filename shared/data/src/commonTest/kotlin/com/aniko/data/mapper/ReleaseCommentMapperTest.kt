package com.aniko.data.mapper

import com.aniko.data.dto.PageableResponseDto
import com.aniko.data.dto.ReleaseCommentDto
import com.aniko.network.AnixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Регрессионный тест на живую верификацию (2026-08-10, `GET release/comment/all/186/0?sort=0`):
 * реальный ответ — обычный `PageableResponse<ReleaseComment>`, с `profile` в виде `ProfileCompact`
 * (не полного `Profile`).
 */
class ReleaseCommentMapperTest {
    private val json =
        """
        {
            "code": 0,
            "content": [
                {
                    "id": 9000006,
                    "profile": {
                        "id": 1000006,
                        "login": "commenter_06",
                        "avatar": "https://s.anixmirai.com/avatars/sample_avatar_06.jpg",
                        "ban_expires": 0,
                        "ban_reason": null,
                        "privilege_level": 0,
                        "badge_id": null,
                        "badge_name": null,
                        "badge_type": null,
                        "badge_url": null,
                        "is_banned": false,
                        "is_sponsor": false,
                        "is_verified": false
                    },
                    "message": "Тестовый комментарий 06.",
                    "timestamp": 1786061960,
                    "type": 0,
                    "vote": 0,
                    "parent_comment_id": null,
                    "vote_count": 0,
                    "likes_count": 0,
                    "is_spoiler": false,
                    "is_edited": false,
                    "is_deleted": false,
                    "is_reply": false,
                    "reply_count": 0,
                    "can_like": true,
                    "posted_at_episode": 12,
                    "release": {
                        "id": 186,
                        "title_ru": "Стальной алхимик: Братство",
                        "title_original": "Fullmetal Alchemist: Brotherhood",
                        "image": "https://s.anixmirai.com/posters/186.jpg"
                    }
                }
            ],
            "current_page": 0,
            "total_page_count": 5,
            "total_count": 88
        }
        """.trimIndent()

    @Test
    fun pageableResponse_decodesReleaseComment() {
        val serializer = PageableResponseDto.serializer(ReleaseCommentDto.serializer())
        val response = AnixJson.decodeFromString(serializer, json)

        assertEquals(0, response.code)
        assertEquals(1, response.content.size)
        assertEquals(5, response.totalPageCount)
        assertEquals(88, response.totalCount)

        val dto = response.content.single()
        assertEquals(9000006L, dto.id)
        assertEquals("Тестовый комментарий 06.", dto.message)
        assertEquals(12, dto.postedAtEpisode)
        assertFalse(dto.isSpoiler)
        assertNull(dto.parentCommentId)
        assertEquals(1000006, dto.profile.id)
        assertEquals("commenter_06", dto.profile.login)
    }

    @Test
    fun releaseCommentDto_toDomain_mapsAuthorThrough() {
        val response = AnixJson.decodeFromString(PageableResponseDto.serializer(ReleaseCommentDto.serializer()), json)
        val domain = response.content.map { it.toDomain() }.single()

        assertEquals(9000006L, domain.id)
        assertEquals(1000006L, domain.author.id)
        assertEquals("commenter_06", domain.author.login)
        assertEquals("https://s.anixmirai.com/avatars/sample_avatar_06.jpg", domain.author.avatarUrl)
        assertFalse(domain.author.isBanned)
        assertEquals(12, domain.postedAtEpisode)
    }

    /**
     * Регрессия на живую находку (2026-09-10, `GET release/comment/all/2804/0?sort=0`): сервер
     * отдаёт поле `release` НЕПОСТОЯННОГО типа между элементами одной страницы — у части элементов
     * это объект (как в [json] выше), у части — голое число (id релиза без остальных полей).
     * Раньше `release: ReleaseDto?` падал `JsonConvertException` на этой форме и ронял ВСЮ
     * страницу комментариев (`AnixContentSlot` показывал «Не удалось загрузить» даже для
     * реального аккаунта с реальными комментариями). Поле удалено из DTO (никем не читалось,
     * см. KDoc `ReleaseCommentDto`) — `ignoreUnknownKeys` теперь просто игнорирует его любой
     * формы, парсинг не падает независимо от того, что сервер пришлёт.
     */
    @Test
    fun releaseCommentDto_decodesWhenReleaseFieldIsNonObjectShape() {
        val jsonWithBareIntRelease =
            """
            {
                "code": 0,
                "content": [
                    {
                        "id": 1,
                        "profile": {"id": 1, "login": "x"},
                        "message": "m",
                        "timestamp": 0,
                        "type": 0,
                        "vote": 0,
                        "parent_comment_id": null,
                        "vote_count": 0,
                        "likes_count": 0,
                        "is_spoiler": false,
                        "is_edited": false,
                        "is_deleted": false,
                        "is_reply": false,
                        "reply_count": 0,
                        "can_like": true,
                        "posted_at_episode": null,
                        "release": 2804
                    }
                ],
                "current_page": 0,
                "total_page_count": 1,
                "total_count": 1
            }
            """.trimIndent()
        val serializer = PageableResponseDto.serializer(ReleaseCommentDto.serializer())

        val response = AnixJson.decodeFromString(serializer, jsonWithBareIntRelease)

        assertEquals(1, response.content.size)
        assertEquals(1L, response.content.single().id)
    }
}
