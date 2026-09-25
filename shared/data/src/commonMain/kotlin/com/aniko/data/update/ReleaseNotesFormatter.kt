package com.aniko.data.update

/**
 * Превращает Markdown release notes в короткий безопасный текст для диалога обновления.
 *
 * Договорённость релизного процесса: ПЕРВАЯ секция второго уровня (`## Что нового …`) — то, что
 * показывается в диалоге; остальное (таблица загрузок, инструкции, ограничения) остаётся на
 * странице релиза. Если секций `##` нет — берётся весь текст. Разметка убирается: заголовки,
 * **жирный**, `код`, ссылки `[текст](url)` → `текст`, алерты `> [!NOTE]`, таблицы, картинки, HTML.
 * Результат ограничен по длине — нельзя раздуть диалог чужим текстом.
 */
fun formatReleaseNotes(
    markdown: String,
    maxChars: Int = DEFAULT_MAX_CHARS,
): String {
    val lines = markdown.replace("\r\n", "\n").lines()
    val section = firstSecondLevelSection(lines) ?: lines
    val cleaned =
        section
            .asSequence()
            .map { it.trimEnd() }
            .filterNot { it.startsWith("|") || it.startsWith("> [!") || it.startsWith("![") || it.startsWith("<") }
            .map(::stripInlineMarkup)
            .map { it.replace(BULLET, "• ").removePrefix("> ") }
            .dropWhile { it.isBlank() }
            .toList()
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    return if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars).trimEnd() + "…"
}

private fun firstSecondLevelSection(lines: List<String>): List<String>? {
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return null
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    return lines.subList(start + 1, end)
}

private fun stripInlineMarkup(line: String): String =
    line
        .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""^#{1,6}\s+"""), "")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")

private val BULLET = Regex("^[-*] ")
private const val DEFAULT_MAX_CHARS = 700
