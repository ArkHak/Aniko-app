package com.aniko.app.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.aniko.ui.i18n.LocalStrings

/**
 * Системное Swing-меню приложения (macOS screen menu bar, P5.T6).
 *
 * Требует `System.setProperty("apple.laf.useScreenMenuBar", "true")`, выставленного до старта
 * Compose-приложения (см. `Main.kt`) — иначе AWT рисует меню как часть окна, а не в системной
 * менюбаре сверху экрана.
 *
 * Меню "Aniko" (заголовок и пункт "About") оставлены литералами намеренно: это системное
 * macOS-меню приложения (аналог `NSApplication` menu), а не часть основного UI-текста — детект
 * [ForbiddenCyrillicStringLiteral] на них не реагирует, т.к. литералы латиницей, но и переводить
 * их через Lyricist избыточно, раз это не пользовательский контент экрана. Пункт "Quit" берётся
 * из `strings.menuQuit`, т.к. это уже осмысленное действие, которое стоит показать на языке
 * пользователя.
 *
 * @param onExit закрытие приложения (`::exitApplication` из `application { }`).
 * @param onBack системное «назад» для desktop (P5.T1 — на десктопе нет аппаратной кнопки back),
 *   дёргает `LocalTitleNavigator.back()` на стороне вызывающего (здесь не завязываемся на
 *   конкретный навигатор, чтобы не тянуть зависимость трека B/commonMain в desktopMain).
 * @param currentLanguageTag текущий выбранный язык (`"en"`/`"ru"`, `null` — системный) для
 *   подсветки выбора в подменю "Language". `null` по умолчанию, если вызывающая сторона ещё не
 *   подключила [com.aniko.data.locale.LocaleStore].
 * @param onLanguageTagChange колбэк смены языка, по умолчанию no-op (заглушка, пока интегратор
 *   не подключит реальный [com.aniko.data.locale.LocaleStore] на уровне `Main.kt`).
 */
@Composable
fun FrameWindowScope.AnixMenuBar(
    onExit: () -> Unit,
    onBack: () -> Unit,
    currentLanguageTag: String? = null,
    onLanguageTagChange: (String?) -> Unit = {},
) {
    val strings = LocalStrings.current

    MenuBar {
        Menu("Aniko") {
            Item(strings.menuAbout, onClick = {})
            Separator()
            Item(
                strings.menuQuit,
                onClick = onExit,
                shortcut = KeyShortcut(Key.Q, meta = true),
            )
        }
        Menu(strings.menuView) {
            Menu(strings.menuLanguage) {
                CheckboxItem(
                    text = "EN",
                    checked = currentLanguageTag == "en",
                    onCheckedChange = { checked -> if (checked) onLanguageTagChange("en") },
                )
                CheckboxItem(
                    text = "RU",
                    checked = currentLanguageTag == "ru",
                    onCheckedChange = { checked -> if (checked) onLanguageTagChange("ru") },
                )
                CheckboxItem(
                    text = strings.galleryLanguageSystem,
                    checked = currentLanguageTag == null,
                    onCheckedChange = { checked -> if (checked) onLanguageTagChange(null) },
                )
            }
        }
        Menu(strings.menuGo) {
            Item(
                strings.menuBack,
                onClick = onBack,
                shortcut = KeyShortcut(Key.LeftBracket, meta = true),
            )
        }
    }
}
