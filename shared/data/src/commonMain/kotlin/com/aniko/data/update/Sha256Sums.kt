package com.aniko.data.update

/** Разбор `SHA256SUMS.txt` в формате `sha256sum`: `<64 hex>  <имя>` (или `<64 hex> *<имя>` для бинарного режима). */
internal object Sha256Sums {
    private val LINE = Regex("""^([0-9a-fA-F]{64})\s+\*?(\S.*)$""")

    /** Имя файла → хэш в нижнем регистре. Строки, не похожие на запись, пропускаются. */
    fun parse(text: String): Map<String, String> =
        text
            .lineSequence()
            .mapNotNull { LINE.matchEntire(it.trim()) }
            .associate { it.groupValues[2].trim() to it.groupValues[1].lowercase() }
}
