package com.aniko.app.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Альфа кружков traffic lights в покое/под курсором (2026-09-18, третий пересмотр — см. подробный
 * KDoc [TrafficLightButtons] про возврат цветных акцентов после нейтрального `2e5f769`): цветные
 * base-цвета уже сами по себе достаточно заметны, поэтому альфа здесь заметно плотнее, чем была у
 * приглушённого нейтрального тона `2e5f769` (0.45/0.85) — [TRAFFIC_LIGHT_ALPHA] держит кружки явно
 * читаемыми как три разных цвета в покое, не «призрачными», [TRAFFIC_LIGHT_HOVER_ALPHA] — полностью
 * непрозрачное усиление под курсором (тот же цвет, максимальная плотность).
 */
private const val TRAFFIC_LIGHT_ALPHA = 0.88f
private const val TRAFFIC_LIGHT_HOVER_ALPHA = 1f

private val TrafficLightDiameter = 12.dp
private val TrafficLightSpacing = 8.dp

/**
 * Три круглые кнопки в стиле macOS traffic lights (close/minimize/zoom) для кастомного
 * оконного хрома (`undecorated = true`, см. `AnikoDesktopChrome.kt`, P5.T6).
 *
 * **Три разных брендовых акцента — цветные кружки, как у нативных macOS traffic lights**
 * (2026-09-18, третий пересмотр этой полосы; коммит `04fd884` красил кружки в три РАЗНЫХ
 * брендовых акцента, `2e5f769` отменил это на единый приглушённый нейтральный тон
 * [MaterialTheme.colorScheme.onSurface] по образцу тайтлбаров VS Code/Linear/Claude Code — но
 * затем владелец продукта явно передумал ещё раз: «давай всё же сделаем цветными»). Формулировка
 * «всё же» (вопреки предыдущему решению) и отсутствие отказа от самой полосы читаются как запрос
 * вернуть цвет именно КНОПКАМ, а не всей полосе целиком — нейтральная заливка полосы + hairline-
 * разделитель снизу (см. `AnikoDesktopChrome.kt`, оставлены без изменений) этому не противоречат:
 * у нативных macOS traffic lights кнопки цветные, а сама область вокруг них в большинстве
 * приложений нейтральна — обычная, не конфликтующая комбинация. Кружки красятся в те же токены
 * дизайн-системы, что и в отменённой правке `04fd884` (они не изобретены заново — сэмплированы
 * из иконки приложения, см. `AnixPalette`):
 * - close → [MaterialTheme.colorScheme.secondary] (кримзон/accent2 — ближе всего по смыслу к
 *   привычному красному "закрыть").
 * - minimize → [AnixThemeTokens.colors.warning] (золото — сэмплировано со звёзд иконки).
 * - zoom (maximize) → [MaterialTheme.colorScheme.primary] (фиолетовый — главный акцент бренда).
 *
 * Все три токена — разные значения на тёмной/светлой теме (см. `AnixPalette`), поэтому кружки
 * адаптируются сами, без ручного ветвления здесь.
 *
 * **Альфа и hover** — из `2e5f769` (см. [TRAFFIC_LIGHT_ALPHA]/[TRAFFIC_LIGHT_HOVER_ALPHA]),
 * применённые теперь к цветным base-цветам вместо нейтрального: базовое значение заметно плотнее,
 * чем было у приглушённого нейтрального тона (цветные точки должны читаться уверенно, раз цель —
 * «цветными»), hover — полная непрозрачность. Тот же механизм —
 * [androidx.compose.foundation.hoverable] на общем `interactionSource` с `clickable` ниже,
 * простое локальное состояние в одном файле без внешней инфраструктуры.
 *
 * `MaterialTheme` здесь разрешается в реальную Anix-схему, а не в дефолтную M3, потому что
 * вызывающий код (`AnikoDesktopChrome`) оборачивает эту функцию в свой собственный
 * `MaterialTheme(colorScheme = anixColorScheme(darkTheme))` — см. его подробный KDoc про то, почему
 * обычный `AppTheme`/`App()` здесь не виден (сиблинг в дереве композиции, не предок).
 *
 * "Zoom" здесь — это [onToggleMaximize] (переключение между `WindowPlacement.Floating` и
 * `WindowPlacement.Maximized`), как и у нативной зелёной кнопки в macOS. Без ripple-индикации
 * нажатия намеренно — нативные traffic lights её тоже не показывают, только hover-глиф, которым
 * здесь сознательно жертвуем ради простоты первой версии (см. отчёт по P5.T6).
 */
@Composable
fun TrafficLightButtons(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = AnixThemeTokens.colors.warning
    Row(modifier = modifier.padding(horizontal = TrafficLightSpacing)) {
        TrafficLightDot(baseColor = MaterialTheme.colorScheme.secondary, onClick = onClose)
        Spacer(Modifier.size(TrafficLightSpacing))
        TrafficLightDot(baseColor = warning, onClick = onMinimize)
        Spacer(Modifier.size(TrafficLightSpacing))
        TrafficLightDot(baseColor = MaterialTheme.colorScheme.primary, onClick = onToggleMaximize)
    }
}

@Composable
private fun TrafficLightDot(
    baseColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val alpha = if (isHovered) TRAFFIC_LIGHT_HOVER_ALPHA else TRAFFIC_LIGHT_ALPHA
    Box(
        modifier =
            Modifier
                .size(TrafficLightDiameter)
                .hoverable(interactionSource)
                .background(
                    color = baseColor.copy(alpha = alpha),
                    shape = CircleShape,
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    )
}
