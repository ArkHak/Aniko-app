package com.aniko.ui.i18n

import com.aniko.model.WeekDay

/**
 * Локализованное имя дня недели (P9.T4). Раньше `ScheduleScreen` рисовал сырой [WeekDay.name]
 * (английские константы enum) — техдолг, явно оставленный на Фазу 9 (см. её же KDoc до этой
 * правки). Та же схема, что и [com.aniko.model.ListStatus.displayName] в `ListStatusStrings.kt`:
 * обычная (не `@Composable`) функция с явным параметром [strings], а не `LocalStrings`-читающий
 * extension — нужна и внутри `ChipRow.label: (T) -> String` (не `@Composable`-лямбда), и снаружи.
 */
fun WeekDay.displayName(strings: Strings): String =
    when (this) {
        WeekDay.MONDAY -> strings.scheduleDayMonday
        WeekDay.TUESDAY -> strings.scheduleDayTuesday
        WeekDay.WEDNESDAY -> strings.scheduleDayWednesday
        WeekDay.THURSDAY -> strings.scheduleDayThursday
        WeekDay.FRIDAY -> strings.scheduleDayFriday
        WeekDay.SATURDAY -> strings.scheduleDaySaturday
        WeekDay.SUNDAY -> strings.scheduleDaySunday
    }
