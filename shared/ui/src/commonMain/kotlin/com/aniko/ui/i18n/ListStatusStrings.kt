package com.aniko.ui.i18n

import com.aniko.model.ListStatus

/**
 * Локализованное отображаемое имя статуса списка. Раньше был отдельный приватный
 * `ListStatus.displayName()`/`toDisplayName()` в `LibraryScreen.kt` и
 * `ReleaseDetailsScreen.kt` — идентичные по смыслу функции с одинаковыми русскими значениями,
 * теперь один источник правды здесь (заодно и для перевода на английский, P2.T9).
 *
 * Обычная (не `@Composable`) функция с явным параметром [strings] — не [LocalStrings]-читающий
 * extension: часть вызовов идёт через `ChipRow.label: (T) -> String`, у которого тип параметра
 * не размечен `@Composable`, поэтому композабл-вызов внутри такой лямбды не скомпилируется.
 * Явный параметр работает в обоих контекстах (внутри и вне `@Composable`-лямбд).
 */
fun ListStatus.displayName(strings: Strings): String =
    when (this) {
        ListStatus.WATCHING -> strings.listStatusWatching
        ListStatus.PLANNED -> strings.listStatusPlanned
        ListStatus.COMPLETED -> strings.listStatusCompleted
        ListStatus.ON_HOLD -> strings.listStatusOnHold
        ListStatus.DROPPED -> strings.listStatusDropped
    }

/** Однобуквенный бейдж статуса поверх постера ([com.aniko.ui.component.ReleaseCard]). */
fun ListStatus.shortLabel(strings: Strings): String =
    when (this) {
        ListStatus.WATCHING -> strings.listStatusShortWatching
        ListStatus.PLANNED -> strings.listStatusShortPlanned
        // "✓" — универсальный символ, не языковой текст, перевода не требует.
        ListStatus.COMPLETED -> COMPLETED_CHECKMARK
        ListStatus.ON_HOLD -> strings.listStatusShortOnHold
        ListStatus.DROPPED -> strings.listStatusShortDropped
    }

private const val COMPLETED_CHECKMARK = "✓"
