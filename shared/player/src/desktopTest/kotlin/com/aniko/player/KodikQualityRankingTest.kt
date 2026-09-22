package com.aniko.player

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ранжирование качеств Kodik ([KodikDirectLinkResolver.decryptAndRank]): порядок «от лучшего к худшему»
 * (он же порядок чипа качества) и нормализация лейблов. Чистые тесты — ни сети, ни libVLC.
 *
 * Регрессия, которую они закрепляют: рейтинг сравнивал нормализованные лейблы (`720p`) со списком голых
 * `1080`/`720`/…, не совпадал ни разу, и «Авто» стартовал с первого ключа ответа — у Kodik они идут по
 * возрастанию (`360p, 480p, 720p`), то есть с ХУДШЕГО качества.
 */
class KodikQualityRankingTest {
    private fun rankedLabels(vararg labels: String): List<String> =
        KodikDirectLinkResolver.decryptAndRank(kodikLinksOf(*labels)).map { (label, _) -> label }

    @Test
    fun hostOrderAscendingIsRankedBestFirst() {
        val ranked = KodikDirectLinkResolver.decryptAndRank(kodikLinksOf("360p", "480p", "720p"))

        assertEquals(listOf("720p", "480p", "360p"), ranked.map { (label, _) -> label })
        // Каждый лейбл остаётся в паре со СВОИМ URL — сортировка не должна их перепутать.
        assertEquals(
            listOf(
                "720p" to kodikPlayableUrl("720p"),
                "480p" to kodikPlayableUrl("480p"),
                "360p" to kodikPlayableUrl("360p"),
            ),
            ranked,
        )
    }

    @Test
    fun bareNumericKeysAreNormalizedToPLabels() {
        assertEquals(listOf("720p", "480p"), rankedLabels("480", "720"))
        assertEquals(listOf("720p", "480p"), rankedLabels("720", "480"))
    }

    @Test
    fun fullHdComesBeforeLowestQualityRegardlessOfHostOrder() {
        assertEquals(listOf("1080p", "360p"), rankedLabels("1080p", "360p"))
        assertEquals(listOf("1080p", "360p"), rankedLabels("360p", "1080p"))
    }

    @Test
    fun fullLadderIsRankedFromBestToWorst() {
        assertEquals(listOf("1080p", "720p", "480p", "360p"), rankedLabels("480p", "1080p", "360p", "720p"))
    }

    @Test
    fun singleQualityIsKeptAsIs() {
        val ranked = KodikDirectLinkResolver.decryptAndRank(kodikLinksOf("480p"))

        assertEquals(listOf("480p" to kodikPlayableUrl("480p")), ranked)
    }

    @Test
    fun unrecognizedLabelsGoLastInHostOrder() {
        // `auto`/`hd` не превращаются в бессмысленные `autop`/`hdp` и не обгоняют настоящие качества.
        assertEquals(listOf("720p", "480p", "auto", "hd"), rankedLabels("auto", "480p", "hd", "720p"))
    }

    @Test
    fun entriesWithoutSrcAreSkipped() {
        val links =
            buildJsonObject {
                put("720p", JsonArray(emptyList()))
                put("480p", kodikLinksOf("480p").getValue("480p"))
            }

        assertEquals(listOf("480p" to kodikPlayableUrl("480p")), KodikDirectLinkResolver.decryptAndRank(links))
    }

    @Test
    fun emptyLinksGiveNoCandidates() {
        assertEquals(emptyList(), KodikDirectLinkResolver.decryptAndRank(JsonObject(emptyMap())))
    }
}
