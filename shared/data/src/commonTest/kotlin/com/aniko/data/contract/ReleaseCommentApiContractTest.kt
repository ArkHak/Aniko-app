package com.aniko.data.contract

import com.aniko.data.api.ReleaseCommentApi
import com.aniko.data.fixtures.ApiFixtures
import com.aniko.data.mapper.toDomain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Контрактный тест `ReleaseCommentApi` (P11.T1) — покрывает фикстуру `releaseCommentAll186Page0`.
 */
class ReleaseCommentApiContractTest {
    @Test
    fun comments_page0_mapsToReleaseCommentsWithRealValues() =
        runTest {
            val client =
                mockAnixClient("/release/comment/all/186/0", ApiFixtures.releaseCommentAll186Page0)
            val api = ReleaseCommentApi(client)

            val page = api.comments(releaseId = 186L, page = 0, sort = 0)

            assertEquals(0, page.code)
            assertEquals(0, page.currentPage)
            assertEquals(15, page.totalPageCount)
            assertEquals(389, page.totalCount)
            assertEquals(1, page.content.size)

            val comment = page.content.single().toDomain()
            assertEquals(9000006L, comment.id)
            assertEquals("Тестовый комментарий 06.", comment.message)
            assertEquals(1786061960L, comment.timestamp)
            assertEquals(12, comment.postedAtEpisode)
            assertEquals(true, comment.canLike)
            assertNull(comment.parentCommentId)

            assertEquals(1000006L, comment.author.id)
            assertEquals("commenter_06", comment.author.login)
            assertEquals(
                "https://s.anixmirai.com/avatars/sample_avatar_06.jpg",
                comment.author.avatarUrl,
            )
            assertEquals(false, comment.author.isBanned)

            assertNotNull(comment.release)
            assertEquals(186, comment.release?.id)
            assertEquals("Темнее Черного: Близнецы и Падающая Звезда", comment.release?.title)
        }
}
