package com.aniko.app.feature.release

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.app.feature.comments.CommentMessage
import com.aniko.app.feature.release.rating.ReleaseRatingSection
import com.aniko.app.navigation.LocalTitleNavigator
import com.aniko.model.Episode
import com.aniko.model.ListStatus
import com.aniko.model.Release
import com.aniko.model.ReleaseComment
import com.aniko.model.VideoHost
import com.aniko.ui.adaptive.AnixWindowSize
import com.aniko.ui.adaptive.LocalAnixWindowSize
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.component.AnixErrorState
import com.aniko.ui.component.AnixIcon
import com.aniko.ui.component.AnixLoadingState
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.share.ShareResult
import com.aniko.ui.share.rememberShareController
import com.aniko.ui.testing.AnixTestTags
import com.aniko.ui.theme.AnixThemeTokens
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Title Detail (P7.T7-T13, Трек C плана): постер, метаданные/жанры/скриншоты, кнопка "Смотреть",
 * статус в списке/избранное, синопсис ([ReleaseHeaderSection]), сетка серий watched/unwatched
 * ([ReleaseEpisodesSection]), похожие/рекомендуемые тайтлы ([ReleaseRelatedSection]), ссылка на
 * комментарии.
 *
 * Вертикальный скролл — один корневой `Column(verticalScroll)`, БЕЗ вложенных `LazyColumn`/
 * `LazyVerticalGrid` того же направления (см. D6 задания трека C и KDoc `EpisodeGrid` про тот же
 * класс проблемы: вложенный ленивый список с неограниченной высотой роняет measure). Комментарии —
 * кликабельная строка-ссылка на отдельный экран ([LocalTitleNavigator.openComments], [CommentsLinkRow])
 * ПЛЮС инлайн-превью первых `COMMENTS_PREVIEW_LIMIT` уже загруженных комментариев
 * (P13.T12, [CommentsPreviewList]) — по той же причине НЕ `LazyColumn`, а обычный bounded `Column`
 * из фиксированного маленького списка: превью не листается, оно затравка перед переходом на
 * [com.aniko.app.feature.comments.ReleaseCommentsScreen], который и остаётся единственной точкой
 * входа с пагинацией/сортировкой/голосованием.
 *
 * **Кнопка "назад"**: `titleNavigator.back()` (P13 [FIX] — живая проверка на Android/iOS вскрыла,
 * что раньше у экрана не было НИКАКОГО способа вернуться назад, кроме системной кнопки Android —
 * на iOS, без edge-swipe в androidx.navigation.compose "из коробки", это был настоящий тупик;
 * `titleNavigator.back()` сам разбирает, где мы — pane-стек на широком экране или NavController на
 * телефоне, см. её KDoc). Track A (design-match-remaining-screens, 2026-09-04): на phone Compact
 * разметка макета (`showDetail`) кладёт эту кнопку прямо поверх hero-обложки шапки (полупрозрачный
 * круг, [ReleaseHeaderSection]), не отдельным `TopAppBar` над контентом — иначе получилась бы
 * дублирующая кнопка назад. На Medium/Expanded (та же `ReleaseDetailsScreen`, встроенная в
 * detail-панель `ListDetailHost`) раскладка НЕ меняется (P13.T13/P5.T3) — `TopAppBar` остаётся.
 */
@Suppress("LongParameterList") // 6 параметров: releaseId/modifier/viewModel — обязательный
// каркас экрана, pendingEpisode*/onEpisodeClick — deep link (P10.T7, см. их собственный KDoc);
// группировать deep-link-параметры в конфиг-класс добавило бы косвенность ради одной пары полей.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailsScreen(
    releaseId: Int,
    modifier: Modifier = Modifier,
    /** Заполняется ТОЛЬКО навигацией из deep link (P10.T7), см. KDoc
     * `AnixDestination.ReleaseDetails`. `null`/`null` — обычный вход на карточку тайтла. */
    pendingEpisodeSourceId: Int? = null,
    pendingEpisodePosition: Int? = null,
    onEpisodeClick: (releaseId: Int, sourceId: Int, position: Int, host: VideoHost) -> Unit = { _, _, _, _ -> },
    viewModel: ReleaseDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId) { viewModel.load(releaseId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val titleNavigator = LocalTitleNavigator.current
    val windowSize = LocalAnixWindowSize.current
    val snackbarHostState = remember { SnackbarHostState() }
    val onShareClick = rememberShareReleaseHandler(snackbarHostState) { state.release }

    HandlePendingEpisodeDeepLink(
        releaseId = releaseId,
        release = state.release,
        sourceId = pendingEpisodeSourceId,
        position = pendingEpisodePosition,
        resolve = viewModel::resolveDeepLinkEpisode,
        onResolved = { target -> onEpisodeClick(releaseId, target.sourceId, target.position, target.host) },
    )

    // См. KDoc функции про кнопку "назад" (P13 [FIX] + Track A: `TopAppBar` только на Medium/Expanded).
    val isCompact = windowSize == AnixWindowSize.Compact
    Scaffold(
        modifier = modifier.testTag(AnixTestTags.RELEASE_DETAILS_SCREEN_ROOT),
        topBar = { if (!isCompact) ReleaseDetailsTopBar(onBack = { titleNavigator.back() }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading && state.release == null -> AnixLoadingState(modifier = Modifier.fillMaxSize())

                state.errorMessage != null && state.release == null ->
                    AnixErrorState(
                        message = state.errorMessage.toReleaseMessage(strings),
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )

                state.release != null ->
                    ReleaseDetailsContent(
                        state = state,
                        onWatchTargetResolved = { target ->
                            onEpisodeClick(releaseId, target.sourceId, target.position, target.host)
                        },
                        resolvePlayTarget = viewModel::resolvePlayTarget,
                        onSelectVoiceType = viewModel::selectVoiceType,
                        onSelectSource = viewModel::selectSource,
                        onEpisodeClick = { sourceId, position, host ->
                            onEpisodeClick(releaseId, sourceId, position, host)
                        },
                        onEpisodeLongClick = viewModel::toggleWatched,
                        onChangeListStatus = viewModel::changeListStatus,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onRetryDetails = viewModel::retryDetails,
                        onOpenTitle = titleNavigator::openTitle,
                        onOpenComments = titleNavigator::openComments,
                        onShareClick = onShareClick,
                        onBackClick = { titleNavigator.back() },
                    )
            }

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** `TopAppBar` с кнопкой "назад" — вынесена отдельно (detekt `LongMethod`), см. её причину в
 *  KDoc [ReleaseDetailsScreen] про фикс P13. Без заголовка: постер/название уже показаны в
 *  контенте ниже, дублировать текст в баре незачем. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseDetailsTopBar(onBack: () -> Unit) {
    val strings = LocalStrings.current
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.clearAndSetSemantics { contentDescription = strings.backContentDescription },
            ) {
                AnixIcon(name = "arrow_back", contentDescription = null)
            }
        },
    )
}

/**
 * Deep link на серию (P10.T7) — резолвит `hostKey` (`ReleaseDetailsViewModel.
 * resolveDeepLinkEpisode`) и доходит до плеера сам, когда данные загрузятся, см. KDoc
 * `AnixDestination.ReleaseDetails`. Вынесена из [ReleaseDetailsScreen] отдельным composable
 * (detekt `LongMethod`/`ComplexCondition` — три независимых guard clauses читаются линейно,
 * общее `&&`-условие того же смысла detekt по умолчанию считает слишком сложным).
 *
 * Срабатывает максимум один раз на конкретную пару [sourceId]/[position] — без флага-защёлки
 * [consumed] эффект дёргался бы повторно при каждой рекомпозиции [release] (например, после
 * toggleFavorite/changeListStatus).
 */
@Suppress("LongParameterList", "ReturnCount") // 6 параметров ровно по числу входов (id/релиз/пара
// deep-link-параметров/резолвер/колбэк), 3 guard clauses читаются линейно — то же обоснование,
// что у `ReleaseDetailsViewModel.resolveDeepLinkEpisodeChain`.
@Composable
private fun HandlePendingEpisodeDeepLink(
    releaseId: Int,
    release: Release?,
    sourceId: Int?,
    position: Int?,
    resolve: suspend (sourceId: Int, position: Int) -> PlayTarget?,
    onResolved: (PlayTarget) -> Unit,
) {
    var consumed by remember(releaseId, sourceId, position) { mutableStateOf(false) }
    if (consumed) return
    if (sourceId == null || position == null) return
    if (release == null) return

    LaunchedEffect(releaseId, sourceId, position) {
        consumed = true
        val target = resolve(sourceId, position)
        if (target != null) onResolved(target)
    }
}

/**
 * Кнопка "Поделиться" (P10.T9) — `shareController.shareText` + Desktop-фоллбэк-снекбар, вынесена
 * из [ReleaseDetailsScreen] отдельной factory-функцией (detekt `LongMethod`).
 */
@Composable
private fun rememberShareReleaseHandler(
    snackbarHostState: SnackbarHostState,
    release: () -> Release?,
): () -> Unit {
    val shareController = rememberShareController()
    val scope = rememberCoroutineScope()
    val shareLinkCopiedMessage = LocalStrings.current.shareLinkCopiedMessage
    return {
        val current = release()
        if (current != null) {
            val result =
                shareController.shareText(
                    text = "${current.title}\n${releaseDeepLink(current.id)}",
                    subject = current.title,
                )
            // Android/iOS открывают нативный шер-диалог сами — там уже есть системная обратная
            // связь, снекбар не нужен (см. KDoc ShareResult). Desktop-фоллбэк ничего не
            // показывает пользователю сам по себе — экран обязан явно подтвердить копирование.
            if (result == ShareResult.COPIED_TO_CLIPBOARD) {
                scope.launch { snackbarHostState.showSnackbar(shareLinkCopiedMessage) }
            }
        }
    }
}

/** `aniko://release/{id}` — тот же формат, что разбирает `parseDeepLink` (`DeepLink.kt`, P10.T7). */
private fun releaseDeepLink(releaseId: Int): String = "aniko://release/$releaseId"

@Suppress("LongParameterList") // Экран-оркестратор — каждый параметр это отдельный обязательный
// колбэк одной из независимых секций (шапка/серии/похожее/комментарии), см. KDoc файла.
@Composable
private fun ReleaseDetailsContent(
    state: ReleaseDetailsUiState,
    onWatchTargetResolved: (PlayTarget) -> Unit,
    resolvePlayTarget: suspend () -> PlayTarget?,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    onChangeListStatus: (ListStatus?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onOpenTitle: (Int) -> Unit,
    onOpenComments: (Int) -> Unit,
    onShareClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    val release = state.release ?: return
    val scope = rememberCoroutineScope()
    // Track A: см. KDoc [ReleaseDetailsScreen] про `isCompact` в `Scaffold.topBar` — тот же
    // признак здесь решает, нужна ли горизонтальная страничная отбивка (`spaceM`) вокруг шапки:
    // Compact-шапка ([ReleaseHeaderSection]) рисует свою hero-обложку край-в-край сама и сама
    // задаёт padding у карточки-нахлёста (18dp по макету, не `spaceM`=16dp), поэтому родительский
    // отступ ей не нужен и даже мешал бы (обложка перестала бы быть во всю ширину). Остальные
    // секции ниже (рейтинг/серии/похожее/комментарии) и Medium/Expanded-раскладка шапки — как и
    // раньше, получают `spaceM` тем же способом, каким раньше был запятнан весь `Column`.
    val isCompact = LocalAnixWindowSize.current == AnixWindowSize.Compact
    val sectionModifier = Modifier.padding(horizontal = dimens.spaceM)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
    ) {
        if (!isCompact) Spacer(modifier = Modifier.height(dimens.spaceM))

        ReleaseHeaderSection(
            release = release,
            details = state.details,
            detailsError = state.detailsError,
            isResolvingPlay = state.isResolvingPlay,
            onWatchClick = {
                scope.launch {
                    val target = resolvePlayTarget()
                    if (target != null) onWatchTargetResolved(target)
                }
            },
            onChangeListStatus = onChangeListStatus,
            onToggleFavorite = onToggleFavorite,
            onRetryDetails = onRetryDetails,
            onShareClick = onShareClick,
            onBackClick = onBackClick,
            modifier = if (isCompact) Modifier else sectionModifier,
        )

        ReleaseRatingSection(releaseId = release.id, averageGrade = release.grade, modifier = sectionModifier)

        ReleaseEpisodesSection(
            state = state,
            onSelectVoiceType = onSelectVoiceType,
            onSelectSource = onSelectSource,
            onEpisodeClick = onEpisodeClick,
            onEpisodeLongClick = onEpisodeLongClick,
            modifier = sectionModifier,
        )

        ReleaseRelatedSection(
            related = state.details?.relatedReleases.orEmpty(),
            recommended = state.details?.recommendedReleases.orEmpty(),
            onOpenTitle = onOpenTitle,
            modifier = sectionModifier,
        )

        ReleaseCommentsSection(
            commentCount = state.details?.commentCount ?: 0,
            preview = state.commentsPreview,
            onOpenComments = { onOpenComments(release.id) },
            modifier = sectionModifier,
        )

        Spacer(modifier = Modifier.height(dimens.spaceM))
    }
}

/**
 * Блок комментариев на Title Detail (P13.T12) — заголовок-ссылка [CommentsLinkRow] на весь
 * счёт комментариев + инлайн-превью первых `state.commentsPreview` под ним, см. KDoc файла.
 * [preview] может быть пустым (ещё грузится/не удалось загрузить/комментариев нет) — тогда
 * рисуется только заголовок, как и раньше до P13.T12.
 */
@Composable
private fun ReleaseCommentsSection(
    commentCount: Int,
    preview: List<ReleaseComment>,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        CommentsLinkRow(commentCount = commentCount, onClick = onOpenComments)
        if (preview.isNotEmpty()) {
            CommentsPreviewList(preview = preview, onCommentClick = onOpenComments)
        }
    }
}

/**
 * Bounded `Column` из уже загруженных превью-комментариев (P13.T12) — НЕ `LazyColumn`, см. KDoc
 * файла: список фиксированный и маленький (`COMMENTS_PREVIEW_LIMIT`), лениво отрисовывать его
 * незачем, а вложенный ленивый список того же направления внутри неограниченного
 * `verticalScroll`-`Column` экрана ронял бы measure (тот же класс проблемы, что и у `EpisodeGrid`).
 */
@Composable
private fun CommentsPreviewList(
    preview: List<ReleaseComment>,
    onCommentClick: () -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        preview.forEach { comment ->
            ReleaseCommentPreviewRow(comment = comment, onClick = onCommentClick)
        }
    }
}

/**
 * Карточка одного превью-комментария (структура по мокапу Claude Design, секция `comments`
 * `Reelwave Prototype.dc.html`: аватар-плейсхолдер + имя + текст на фоне `surfaceVariant`).
 *
 * Текст/спойлер — [CommentMessage] (`feature/comments`, P13.T12) — переиспользует ТУ ЖЕ логику
 * раскрытия спойлера, что и полный `CommentRow` на [com.aniko.app.feature.comments.ReleaseCommentsScreen]
 * (D10: плашка "Показать спойлер" вместо `Modifier.blur`, см. KDoc [CommentMessage] про причину —
 * `blur` не гарантирован на Android <12/iOS-Skia). Мокап рисует спойлер размытым текстом — здесь
 * сознательно то же архитектурное решение D10, что и на полном экране комментариев, а не второй,
 * несовместимый способ показа спойлера в том же приложении.
 *
 * Вся карточка кликабельна ([onClick] — переход на [com.aniko.app.feature.comments.ReleaseCommentsScreen],
 * та же точка входа, что и у [CommentsLinkRow]) — превью не умеет лайкать/отвечать само по себе,
 * это по-прежнему функциональность только полного экрана.
 */
@Composable
private fun ReleaseCommentPreviewRow(
    comment: ReleaseComment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val shape = RoundedCornerShape(dimens.cornerM)
    // Track A (design-match-remaining-screens, 2026-09-04): точные токены разметки `comments` —
    // фон `overlay045` + бордер `overlay07` (было: непрозрачность 0.6 поверх `surfaceVariant`,
    // без бордера вовсе), padding 12dp (было 16dp/`spaceM`), имя 12/700 (было `labelMedium`
    // 12/500), текст 12.5px на `textSecondary75` (было `bodySmall` 12/400 на дефолтном
    // `onSurface`).
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.overlay045, shape)
                .border(BorderStroke(COMMENT_PREVIEW_BORDER_WIDTH, colors.overlay07), shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(dimens.space12),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixAvatar(
            avatarUrl = comment.author.avatarUrl,
            login = comment.author.login,
            size = COMMENT_PREVIEW_AVATAR_SIZE,
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            Text(
                text = comment.author.login,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
            CommentMessage(
                comment = comment,
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = COMMENT_PREVIEW_TEXT_SIZE,
                        color = colors.textSecondary75,
                    ),
            )
        }
    }
}

/**
 * Заголовок блока комментариев (P7.T12 вход, D6) — кликабельная строка-ссылка на весь экран
 * комментариев, отдельно от инлайн-превью под ней ([CommentsPreviewList], P13.T12, см. KDoc
 * файла) — единственная точка входа с пагинацией/сортировкой/голосованием остаётся полный экран.
 * `Strings.releaseCommentsTitle(count)` уже несёт число, поэтому текст самодостаточен без
 * дополнительной подписи.
 */
@Composable
private fun CommentsLinkRow(
    commentCount: Int,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val title = strings.releaseCommentsTitle(commentCount)

    // Подтверждено на устройстве (Фаза 11, T9): Text{} не сливается с кликабельным Row сам по
    // себе — тот же паттерн, что и остальные M3/самописные clickable-строки этой фазы.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .clearAndSetSemantics { contentDescription = title },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Track A (design-match-remaining-screens, 2026-09-04): заголовки секций карточки Title
        // Detail на макете — 14px/700 (было `titleMedium` — 16px/700), тот же приём, что и
        // `HeroSectionTitle` в `ReleaseHeaderSection.kt`/секция серий в `ReleaseEpisodesSection.kt`.
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = COMMENTS_TITLE_SIZE,
                    fontWeight = FontWeight.Bold,
                ),
        )
        AnixIcon(name = "arrow_forward", contentDescription = null)
    }
}

/** См. `ReleaseDetailsViewModel.COMMENTS_PREVIEW_LIMIT` (та же цифра, дублируется намеренно —
 *  экран не должен знать о деталях загрузки, вьюмодель не должна знать о рендере). */
private val COMMENT_PREVIEW_AVATAR_SIZE = 30.dp
private val COMMENT_PREVIEW_BORDER_WIDTH = 1.dp
private val COMMENT_PREVIEW_TEXT_SIZE = 12.5.sp
private val COMMENTS_TITLE_SIZE = 14.sp
