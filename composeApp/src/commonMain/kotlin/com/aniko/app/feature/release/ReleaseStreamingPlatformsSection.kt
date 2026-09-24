package com.aniko.app.feature.release

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniko.model.Episode
import com.aniko.model.ReleaseStreamingPlatform
import com.aniko.model.VideoHost
import com.aniko.ui.component.AnixAsyncImage
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Легальные стриминг-площадки релиза (Title Detail) — честно отражает то, что вернул
 * `GET release/streaming/platform/{releaseId}` (сверено вживую 2026-09-23, см. KDoc
 * `ReleaseStreamingPlatformDto` в `shared/data`), БЕЗ какой-либо гео-логики на клиенте: сервер
 * сам решает, какие площадки вернуть для данного запроса, приложению остаётся только отрисовать
 * список и открыть [ReleaseStreamingPlatform.url] по тапу.
 *
 * [platforms] пустой — обычный случай (`content: []` в живых сэмплах, у релиза нет легальных
 * площадок) — секция в этом случае вообще не рендерится (в отличие от оригинального
 * `ReleaseStreamingPlatformUiController`, который рисует пустой стейт: здесь это не основной
 * сценарий, пустая секция была бы лишним визуальным шумом на карточке большинства релизов).
 *
 * Тап открывает [ReleaseStreamingPlatform.url] через [LocalUriHandler] — стандартный механизм
 * Compose Multiplatform, `expect/actual` не требуется (одна и та же реализация на Android/iOS/
 * Desktop).
 */
@Composable
fun ReleaseStreamingPlatformsSection(
    platforms: List<ReleaseStreamingPlatform>,
    modifier: Modifier = Modifier,
) {
    if (platforms.isEmpty()) return
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        // Тот же визуальный язык заголовков секций карточки Title Detail, что и у
        // `HeroSectionTitle`/`ReleaseEpisodesSection`/`CommentsLinkRow` (14px/700).
        Text(
            text = strings.releaseStreamingPlatformsTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = SECTION_TITLE_SIZE),
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            platforms.forEach { platform ->
                StreamingPlatformRow(platform = platform, strings = strings)
            }
        }
    }
}

/**
 * Одна строка площадки: иконка + название, вся строка кликабельна (не только иконка/текст по
 * отдельности) — тот же паттерн `clickable` + `clearAndSetSemantics(Role.Button)`, что и у
 * `ReleaseCommentPreviewRow`/`CommentsLinkRow` в `ReleaseDetailsScreen.kt`.
 * `heightIn(min = minTouchTarget)` — доступность (WCAG/Material, см. AGENTS.md).
 */
@Composable
private fun StreamingPlatformRow(
    platform: ReleaseStreamingPlatform,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(dimens.cornerM)
    val description = strings.releaseStreamingPlatformOpenContentDescription(platform.name)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTouchTarget)
                .clip(shape)
                .background(colors.overlay045, shape)
                .clickable(onClickLabel = description, role = Role.Button) {
                    uriHandler.openUri(platform.url)
                }.clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }.padding(horizontal = dimens.space12, vertical = dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixAsyncImage(
            model = platform.iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(PLATFORM_ICON_SIZE).clip(RoundedCornerShape(dimens.cornerS)),
        )
        Text(
            text = platform.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Короткая текст-подсказка вместо обычного флоу выбора неофициального источника, когда
 * воспроизведение заблокировано ([ReleaseDetailsUiState.isLicensedPlaybackBlocked], см.
 * `ReleaseDetailsScreen.ReleaseDetailsContent`) — своя формулировка, НЕ калька с уведомления
 * Google (см. задание).
 */
@Composable
fun ThirdPartyPlatformsDisabledHint(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Text(
        text = strings.releaseThirdPartyPlatformsDisabledHint,
        style = MaterialTheme.typography.bodySmall,
        color = AnixThemeTokens.colors.textSecondary60,
        modifier = modifier,
    )
}

/**
 * [ReleaseStreamingPlatformsSection] + обычный флоу выбора источника ([ReleaseEpisodesSection])
 * — вынесено из `ReleaseDetailsScreen.ReleaseDetailsContent` отдельной функцией (detekt
 * `LongMethod`/`TooManyFunctions` на `ReleaseDetailsScreen.kt`; живёт здесь, а не там, т.к.
 * тематически про легальные площадки). `ColumnScope`-расширение, а не обёртка в свой `Column` —
 * секции остаются прямыми детьми внешней колонки `ReleaseDetailsContent` с тем же
 * `Arrangement.spacedBy`, что и остальные секции (тот же приём, что и `RegisterForm`/`LoginForm`
 * в `feature/auth`), без лишнего вложенного отступа.
 *
 * Площадки показываются ВСЕГДА независимо от блокировки воспроизведения (секция сама не
 * рендерится при пустом списке, см. её KDoc выше); когда воспроизведение заблокировано
 * ([ReleaseDetailsUiState.isLicensedPlaybackBlocked] — флаг `isThirdPartyPlatformsDisabled`,
 * `note` о лицензировании или непустой список площадок), они заменяют собой саму
 * [ReleaseEpisodesSection] (+ [ThirdPartyPlatformsDisabledHint] вместо кнопки "Смотреть",
 * уже скрытой в `ReleaseHeaderSection` — см. её же KDoc про `hideWatchAction`).
 */
@Suppress("LongParameterList") // Координирующий блок: состояние + 4 колбэка эпизодов — тот же
// набор параметров, что и у самой [ReleaseEpisodesSection], плюс модификатор секции.
@Composable
fun ColumnScope.EpisodesOrStreamingPlatformsSection(
    state: ReleaseDetailsUiState,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    sectionModifier: Modifier,
) {
    ReleaseStreamingPlatformsSection(platforms = state.streamingPlatforms, modifier = sectionModifier)

    if (state.isLicensedPlaybackBlocked) {
        ThirdPartyPlatformsDisabledHint(modifier = sectionModifier)
    } else {
        ReleaseEpisodesSection(
            state = state,
            onSelectVoiceType = onSelectVoiceType,
            onSelectSource = onSelectSource,
            onEpisodeClick = onEpisodeClick,
            onEpisodeLongClick = onEpisodeLongClick,
            modifier = sectionModifier,
        )
    }
}

private val SECTION_TITLE_SIZE = 14.sp
private val PLATFORM_ICON_SIZE = 32.dp
