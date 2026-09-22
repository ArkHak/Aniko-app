package com.aniko.ui.component

/**
 * Имя иконки Material Symbols (то же имя, что принимает [AnixIcon]) → Unicode-codepoint в
 * `material_symbols_rounded.ttf` (`shared/ui/src/commonMain/composeResources/font/`).
 *
 * Ровно те 32 имени, что subsetting сохранил в шрифте (см. KDoc [AnixIcon]) — если добавляется
 * новое имя иконки в проект, сначала нужно добавить его codepoint сюда, а сам глиф — в шрифт
 * (пересобрать subset через `fonttools subset`), иначе [AnixIcon] упадёт с `error(...)`.
 */
internal object MaterialSymbolsCodepoints {
    // именовать каждый константой ради detekt только затемнило бы таблицу "имя → codepoint".
    @Suppress("MagicNumber") // Unicode-codepoint'ы из Material Symbols, не произвольные числа —
    val map: Map<String, Int> =
        mapOf(
            "home" to 0xe9b2,
            "grid_view" to 0xe9b0,
            "view_list" to 0xe8ef,
            "bookmark" to 0xe8e7,
            "calendar_month" to 0xebcc,
            "person" to 0xf0d3,
            "favorite" to 0xe87e,
            "star" to 0xf09a,
            "arrow_back" to 0xe5c4,
            "arrow_forward" to 0xe5c8,
            "help" to 0xe8fd,
            "search" to 0xef7a,
            "video_library" to 0xe04a,
            "shuffle" to 0xe043,
            "swap_vert" to 0xe8d5,
            "visibility" to 0xe8f4,
            "visibility_off" to 0xe8f5,
            "play_circle" to 0xe1c4,
            "play_arrow" to 0xe037,
            "pause" to 0xe034,
            "share" to 0xe80d,
            "close" to 0xe5cd,
            "settings" to 0xe8b8,
            "fullscreen" to 0xe5d0,
            "picture_in_picture_alt" to 0xe911,
            "replay_10" to 0xe059,
            "forward_10" to 0xe056,
            "check_circle" to 0xf0be,
            "radio_button_unchecked" to 0xe836,
            "skip_next" to 0xe044,
            "cloud_off" to 0xe2c1,
            "notifications" to 0xe7f5,
        )
}
