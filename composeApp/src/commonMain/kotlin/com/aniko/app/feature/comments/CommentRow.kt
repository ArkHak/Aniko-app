package com.aniko.app.feature.comments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aniko.model.ReleaseComment
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Строка одного комментария (P7.T12).
 *
 * Текст/спойлер — вынесены в переиспользуемый [CommentMessage] (P13.T12: тот же виджет теперь
 * рисует и инлайн-превью комментариев на Title Detail, см. `ReleaseCommentPreviewRow` в
 * `feature/release`, без дублирования логики раскрытия спойлера).
 *
 * [effectiveVote] — голос текущего пользователя с учётом локального оптимистичного оверрайда
 * (см. `CommentsState.voteOverrides`), по умолчанию — сырое значение из модели (для превью/
 * вызова без ViewModel). [onVoteClick] получает уже посчитанный НОВЫЙ голос (toggle: `1` если
 * ещё не лайкнут, `0` если уже лайкнут) — caller просто прокидывает его в `CommentRepository.vote`.
 */
@Composable
fun CommentRow(
    comment: ReleaseComment,
    modifier: Modifier = Modifier,
    effectiveVote: Int = comment.vote,
    onVoteClick: (commentId: Long, vote: Int) -> Unit = { _, _ -> },
) {
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors

    // Простой отступ вместо полного дерева ответов (допустимо по заданию P7.T12) — реплаи
    // визуально вложены под родительский комментарий, без соединительных линий/сворачивания веток.
    val indent = if (comment.isReply) dimens.spaceL else 0.dp
    val cardShape = RoundedCornerShape(dimens.cornerM)

    // Track A (design-match-remaining-screens, 2026-09-04): карточка комментария под макет
    // `showDetail`/`comments` — тот же язык (`overlay045` фон + `overlay07` бордер, radius 12,
    // padding 12), что уже использует превью-карточка на Title Detail
    // (`ReleaseCommentPreviewRow` в `ReleaseDetailsScreen.kt`) — раньше здесь была голая строка
    // без контейнера вовсе.
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = indent)
                .clip(cardShape)
                .background(colors.overlay045, cardShape)
                .border(BorderStroke(COMMENT_CARD_BORDER_WIDTH, colors.overlay07), cardShape)
                .padding(dimens.space12),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixAvatar(avatarUrl = comment.author.avatarUrl, login = comment.author.login, size = AVATAR_SIZE)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
        ) {
            Text(
                text = comment.author.login,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )

            CommentMessage(comment = comment)

            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoteIndicator(
                    likesCount = comment.likesCount,
                    isVoted = effectiveVote == LIKE_VOTE,
                    onClick = {
                        onVoteClick(comment.id, if (effectiveVote == LIKE_VOTE) NO_VOTE else LIKE_VOTE)
                    },
                )
                if (comment.replyCount > 0) {
                    Text(
                        text = strings.commentReplyCount(comment.replyCount.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Текст комментария с учётом спойлера (D10, обязательное архитектурное решение — см. таск P7.T12
 * в плане): БЕЗ `Modifier.blur` — на Android <12 `RenderEffect`/`blur` no-op (требует API 31),
 * поведение на iOS/Skia не гарантировано, а полная сборка iOS-приложения даже не входит в CI (см.
 * журнал Фазы 6 плана про `-lsqlite3`/`ios.yml`), риск незамеченной регрессии слишком велик.
 * Вместо этого при `comment.isSpoiler` текст ВООБЩЕ не композится до тапа — замещается плашкой
 * [SpoilerPlaceholder] на фоне `surfaceVariant`. Раскрытое состояние живёт в `rememberSaveable`
 * с ключом по `comment.id` — переживает поворот экрана/рекомпозицию, не обязано переживать
 * перезапуск процесса (см. задание). Корректно и для скринридера: до раскрытия он видит только
 * метку "Спойлер — нажмите, чтобы показать", не текст спойлера.
 *
 * Публичная (не `private`), т.к. переиспользуется вне [CommentRow] — превью комментариев на Title
 * Detail (P13.T12, `ReleaseCommentPreviewRow`) должно раскрывать спойлеры тем же способом, а не
 * заново решать D10 в своём коде. [textStyle] параметризуем: полный список комментариев и
 * компактное превью на Detail используют разный размер текста.
 */
@Composable
fun CommentMessage(
    comment: ReleaseComment,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val strings = LocalStrings.current
    var isSpoilerRevealed by rememberSaveable(comment.id) { mutableStateOf(false) }

    if (comment.isSpoiler && !isSpoilerRevealed) {
        SpoilerPlaceholder(
            label = strings.commentsSpoilerLabel,
            onReveal = { isSpoilerRevealed = true },
            baseStyle = textStyle,
            modifier = modifier,
        )
    } else {
        Text(text = comment.message, style = textStyle, modifier = modifier)
    }
}

/** Плашка вместо текста спойлера — см. D10 в KDoc [CommentMessage]. Высота — `defaultMinSize`, а
 *  не жёсткий `.height()`: должна расти вместе с текстом при масштабе шрифта (тот же баг класс,
 *  что нашёл аудит P6.T12 у `ListStatusChip`, см. журнал Фазы 6 плана). */
/**
 * Track A (design-match-remaining-screens, 2026-09-04): подпись плашки перекрашена в accent
 * (`MaterialTheme.colorScheme.primary`, см. KDoc [EpisodeGrid][com.aniko.ui.component.EpisodeGrid]
 * про то же самое равенство primary/accent после Track A) и выведена жирным — раньше была
 * нейтральным `onSurfaceVariant` без акцента, как обычный неактивный текст, хотя визуально это
 * кликабельный призыв к действию. [baseStyle] — тот же `textStyle`, что получил бы обычный текст
 * комментария у конкретного вызывающего кода (полный список — крупнее, превью на Title Detail —
 * мельче), чтобы плашка не "прыгала" размером относительно окружающего текста.
 */
@Composable
private fun SpoilerPlaceholder(
    label: String,
    onReveal: () -> Unit,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = SPOILER_PLACEHOLDER_MIN_HEIGHT)
                .clip(RoundedCornerShape(dimens.cornerS))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onReveal)
                .padding(dimens.spaceM),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = baseStyle.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Лайк комментария — только отображение/toggle подсветки, без иконок material-icons-extended
 *  (не подключена в `composeApp`, см. `build.gradle.kts`) — заменена текстовым индикатором.
 *
 * P11.T7/T11 (трек D): [color] при `isVoted` раньше брал `MaterialTheme.colorScheme.primary` —
 * на элевейтед-поверхности в тёмной теме не проходит контраст 4.5:1 (находка аудита фундамента
 * Фазы 11). Заменено на `AnixThemeTokens.colors.primaryText` — текстовый вариант акцентного
 * цвета, специально подобранный под контраст на surface (см. `Color.kt`).
 *
 * P11.T7: `contentDescription` раньше был просто числом лайков (`likesCount.toString()`) —
 * скринридер озвучивал голое число без какого-либо смысла действия/состояния. Теперь описывает
 * и действие (поставить/убрать лайк, по [isVoted]), и счётчик.
 */
@Composable
private fun VoteIndicator(
    likesCount: Int,
    isVoted: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val color = if (isVoted) AnixThemeTokens.colors.primaryText else MaterialTheme.colorScheme.onSurfaceVariant
    val voteDescription =
        if (isVoted) strings.commentUnlikeContentDescription else strings.commentLikeContentDescription
    Text(
        text = "$LIKE_MARK $likesCount",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier =
            Modifier
                .clip(RoundedCornerShape(AnixThemeTokens.dimens.cornerPill))
                .clickable(role = Role.Button, onClick = onClick)
                // TalkBack не сливает Text-потомка с кликабельным родителем в этой версии CMP
                // (см. тот же приём в NavigationBarSlot.kt/ProfileScreen.kt) — contentDescription
                // нужно ставить явно через clearAndSetSemantics, обычный semantics{} недостаточен.
                .clearAndSetSemantics {
                    contentDescription = "$voteDescription, $likesCount"
                    role = Role.Button
                }.padding(vertical = VOTE_INDICATOR_VERTICAL_PADDING),
    )
}

private const val LIKE_VOTE = 1
private const val NO_VOTE = 0
private const val LIKE_MARK = "▲"

// Track A: 30dp — тот же размер, что и `ReleaseCommentPreviewRow` на Title Detail (см. её
// `COMMENT_PREVIEW_AVATAR_SIZE` в `ReleaseDetailsScreen.kt`) и разметка макета `comments` (было
// 40dp — крупнее, чем в мокапе).
private val AVATAR_SIZE = 30.dp
private val SPOILER_PLACEHOLDER_MIN_HEIGHT = 48.dp
private val VOTE_INDICATOR_VERTICAL_PADDING = 2.dp
private val COMMENT_CARD_BORDER_WIDTH = 1.dp
