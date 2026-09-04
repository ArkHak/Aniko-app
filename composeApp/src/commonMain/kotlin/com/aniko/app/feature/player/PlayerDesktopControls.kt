package com.aniko.app.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Единственные контролы плеера на Desktop — там, где `EmbedVideoController.isSupported == false`.
 *
 * Почему их всего два и почему без прогресс-бара/таймера (решение P8.T1, зафиксировано в
 * `docs/REELWAVE_PLAN.md`, не пересматривать): на Desktop видео играет в **системном браузере**
 * (`EmbedPlayer.desktop.kt` → `Desktop.browse`), а не внутри окна приложения. Позиции
 * воспроизведения, длительности и события «серия кончилась» на этой платформе физически не
 * существует — значит:
 * - баннер «следующая серия через Nс» (P8.T4) здесь невозможен, отсчитывать нечего. Его заменяет
 *   постоянно видимая кнопка «Следующая серия» — переход по решению пользователя,
 *   а не по таймингу видео;
 * - авто-отметка «просмотрено» (P8.T8) здесь тоже невозможна — её заменяет ручной toggle рядом.
 *
 * Расположение — нижняя панель по центру окна: `EmbedPlayerView` на Desktop рисует заглушку
 * «Opened in the system browser» строго по центру экрана, и низ у неё свободен.
 *
 * На Android/iOS показывается только в вырожденном случае, когда `isSupported == false` и там
 * (старый системный WebView без нужных фич `androidx.webkit`, см. KDoc `EmbedVideoController.
 * isSupported`). Отдельной ветки под это не заводим: причина одна и та же — позиции
 * воспроизведения нет, — значит и деградация одна и та же.
 *
 * Оверлея (P8.T3) и панели скорости (P8.T5) здесь нет намеренно: рисовать поверх заглушки
 * контролы, которые ничем не управляют, — прямое враньё пользователю.
 */
@Composable
fun PlayerDesktopControls(
    isWatched: Boolean,
    hasNextEpisode: Boolean,
    onToggleWatched: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Surface(
        modifier = modifier.padding(dimens.spaceM),
        shape = RoundedCornerShape(dimens.cornerM),
        tonalElevation = dimens.spaceXs,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.space12, vertical = dimens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onToggleWatched) {
                AnixIcon(
                    name = if (isWatched) "check_circle" else "radio_button_unchecked",
                    contentDescription = null,
                    filled = isWatched,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = if (isWatched) strings.playerMarkUnwatched else strings.playerMarkWatched,
                    modifier = Modifier.padding(start = dimens.spaceS),
                )
            }
            if (hasNextEpisode) {
                Button(onClick = onNextEpisode) {
                    AnixIcon(
                        name = "skip_next",
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(text = strings.playerNextEpisode, modifier = Modifier.padding(start = dimens.spaceS))
                }
            }
        }
    }
}
