package com.aniko.app.feature.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.model.ReleaseComment
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран комментариев к релизу — минимальная версия для Фазы 5 (P5.T2): первая страница
 * (`ReleaseCommentApi.comments(releaseId, page = 0, sort = 0)`), без пагинации/сортировки/
 * спойлер-блюра. Полноценный экран с этими блоками — Фаза 7 (P7.T12); здесь задача только встроить
 * маршрут в pane-систему P5.T3 (`ListDetailHost`) и в `NavHost` на compact-экранах.
 */
@Composable
fun ReleaseCommentsScreen(
    releaseId: Int,
    modifier: Modifier = Modifier,
    viewModel: CommentsViewModel = koinViewModel(),
) {
    LaunchedEffect(releaseId) { viewModel.load(releaseId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val dimens = AnixThemeTokens.dimens

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = strings.commentsTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(dimens.spaceM),
            )

            when {
                state.isLoading && state.comments.isEmpty() -> AnixLoadingBox(modifier = Modifier.fillMaxSize())

                state.errorMessage != null && state.comments.isEmpty() ->
                    AnixErrorBox(
                        message = state.errorMessage.toCommentsMessage(strings),
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )

                // Пустой список без ошибки (у релиза правда нет комментариев) — намеренно без
                // отдельного текста-заглушки: нет подходящего i18n-ключа под "нет комментариев" в
                // `Strings` (shared/ui для этой задачи трогать нельзя), заводить новый ради Фазы 5
                // избыточно — полноценные пустые состояния экрана комментариев доделает Фаза 7.
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(dimens.spaceM),
                        verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
                    ) {
                        items(state.comments, key = { it.id }) { comment ->
                            CommentRow(comment)
                        }
                    }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: ReleaseComment) {
    val dimens = AnixThemeTokens.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixAvatar(avatarUrl = comment.author.avatarUrl, login = comment.author.login, size = AVATAR_SIZE)
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
            Text(text = comment.author.login, style = MaterialTheme.typography.labelLarge)
            Text(text = comment.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** `homeSectionLoadError` ("Couldn't load" / "Не удалось загрузить") — самый нейтральный из уже
 * существующих ключей, подходит как generic-фолбэк без заведения нового (см. KDoc выше про
 * shared/ui). */
private fun LoadError?.toCommentsMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.homeSectionLoadError
    }

private val AVATAR_SIZE = 40.dp
