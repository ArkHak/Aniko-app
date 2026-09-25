package com.aniko.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseNotesFormatterTest {
    @Test
    fun takesOnlyTheFirstSecondLevelSection() {
        val md =
            """
            # Aniko v0.2.0 — релиз

            > [!NOTE]
            > Ранний релиз.

            ## Что нового с v0.1.0

            **Дизайн**
            - Единый [дизайн](https://example.com) на `всех` платформах

            ## Скачать

            | Платформа | Файл |
            |---|---|
            """.trimIndent()

        val text = formatReleaseNotes(md)

        assertEquals("Дизайн\n• Единый дизайн на всех платформах", text)
    }

    @Test
    fun withoutSectionsUsesWholeTextWithoutMarkup() {
        val text = formatReleaseNotes("# Заголовок\n\n- **Первое**\n- Второе")

        assertEquals("Заголовок\n\n• Первое\n• Второе", text)
    }

    @Test
    fun truncatesLongTextAndDropsHtmlAndImages() {
        val md = "## Что нового\n<div>x</div>\n![img](a.png)\n" + "слово ".repeat(400)

        val text = formatReleaseNotes(md, maxChars = 50)

        assertTrue(text.length <= 51)
        assertTrue(text.endsWith("…"))
        assertFalse(text.contains("<div>") || text.contains("!["))
    }

    @Test
    fun emptyNotesGiveEmptyText() {
        assertEquals("", formatReleaseNotes(""))
    }
}
