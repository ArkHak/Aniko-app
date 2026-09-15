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
 * Фолбэк-контролы плеера для случая `EmbedVideoController.isSupported == false` — когда моста
 * нет вообще ни на какой платформе (не Desktop-специфичная ветка: пересмотр P8.T1 на
 * `feature/desktop-video-player` дал Desktop реальный VLCJ-мост, `isSupported == true` там теперь
 * безусловное статическое значение; эта ветка UI осталась как честная деградация на случай старого
 * системного WebView на Android без нужных фич `androidx.webkit`, см. KDoc
 * `EmbedVideoController.isSupported`). Провал резолва конкретного потока (мёртвая
 * ссылка/неподдерживаемый хост, `DesktopStreamResolver` вернул `null`) — ДРУГОЙ, менее суровый
 * случай (`isSupported` остаётся `true`, деградирует только `EmbedVideoState.isVideoFound`) — им
 * занимается `PlayerOverlay`, не этот компонент, см. её KDoc про `bridgeActive`.
 *
 * Почему их всего два (плюс кнопка «Назад», добавленная ревью замечанием #5) и почему без
 * прогресс-бара/таймера: без моста позиции воспроизведения, длительности и события «серия
 * кончилась» не существует — значит:
 * - баннер «следующая серия через Nс» (P8.T4) здесь невозможен, отсчитывать нечего. Его заменяет
 *   постоянно видимая кнопка «Следующая серия» — переход по решению пользователя,
 *   а не по таймингу видео;
 * - авто-отметка «просмотрено» (P8.T8) здесь тоже невозможна — её заменяет ручной toggle рядом.
 *
 * Кнопка «Назад» (2026-09-10, ревью замечание #5): до этой правки в этой ветке UI не было НИ
 * ОДНОГО способа вернуться из плеера в меню — `onBack`, доступный в `PlayerScreen.kt`, никуда не
 * был прокинут (штатный путь с мостом использует `PlayerOverlay`/`CompactPlayerChrome`, у которых
 * кнопка "Назад" есть всегда, без гейтов на `isSupported`/состояние видео — см. их KDoc). Кнопка
 * здесь — тот же принцип: всегда видна, ничем не гейтится (реального видео-состояния и так нет,
 * гейтить нечем).
 *
 * Расположение — нижняя панель по центру окна: `EmbedPlayerView` в деградационном случае рисует
 * статус ошибки строго по центру, и низ у неё свободен.
 *
 * Оверлея (P8.T3) и панели скорости (P8.T5) здесь нет намеренно: рисовать поверх заглушки
 * контролы, которые ничем не управляют, — прямое враньё пользователю.
 *
 * `@Suppress("LongParameterList")`: плоский набор колбэков без бизнес-логики (тот же случай, что
 * у `OverlayIconButton`/`AnixSessionGate` — см. их KDoc); группировка в data class ради обхода
 * линта добавила бы косвенность без пользы.
 */
@Composable
@Suppress("LongParameterList")
fun PlayerDesktopControls(
    isWatched: Boolean,
    hasNextEpisode: Boolean,
    onBack: () -> Unit,
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
            OutlinedButton(onClick = onBack) {
                AnixIcon(
                    name = "arrow_back",
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(text = strings.backContentDescription, modifier = Modifier.padding(start = dimens.spaceS))
            }
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
