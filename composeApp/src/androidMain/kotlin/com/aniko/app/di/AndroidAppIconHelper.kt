package com.aniko.app.di

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.aniko.data.theme.AppIconStore

/**
 * Android-реализация [AppIconHelper] (P16.T21): включает `activity-alias`, соответствующий
 * выбранной иконке, и отключает остальные (включая саму `MainActivity`, когда выбран
 * НЕ-`"main"` вариант) через [PackageManager.setComponentEnabledSetting].
 *
 * `PackageManager.DONT_KILL_APP` — процесс не убивается при смене состояния компонента: без
 * этого флага система перезапускала бы приложение сразу после переключения иконки в настройках,
 * что выглядело бы как краш. Системный лаунчер подхватывает новую иконку и лейбл при следующем
 * обновлении своего кэша (не мгновенно на всех прошивках — стандартное поведение Android, не
 * баг этой реализации).
 *
 * Ключи в [supportedIcons] совпадают с [AppIconStore.SUPPORTED_ICONS] и с суффиксами
 * `activity-alias` в `AndroidManifest.xml` (`.MainActivity.Classic`/`.Dream`/`.Ice`); `"main"` —
 * единственный ключ без алиаса, это сама `MainActivity`.
 */
class AndroidAppIconHelper(
    private val context: Context,
) : AppIconHelper {
    override val supportedIcons: List<String> = AppIconStore.SUPPORTED_ICONS.toList()

    override fun apply(iconKey: String?) {
        val selected = iconKey?.takeIf { it in supportedIcons } ?: AppIconStore.DEFAULT_ICON
        val packageManager = context.packageManager
        ICON_COMPONENTS.forEach { (key, className) ->
            val state =
                if (key == selected) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, className),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private companion object {
        const val MAIN_ACTIVITY = "com.aniko.app.MainActivity"

        val ICON_COMPONENTS =
            listOf(
                "main" to MAIN_ACTIVITY,
                "classic" to "$MAIN_ACTIVITY.Classic",
                "dream" to "$MAIN_ACTIVITY.Dream",
                "ice" to "$MAIN_ACTIVITY.Ice",
            )
    }
}
