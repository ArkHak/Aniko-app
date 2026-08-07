package com.aniko.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aniko.model.FriendRequestVisibility
import com.aniko.model.PrivacyVisibility
import com.aniko.model.ProfileDetails
import com.aniko.model.ProfilePrivacy
import com.aniko.ui.component.AnixAvatar
import com.aniko.ui.component.AnixErrorBox
import com.aniko.ui.component.AnixLoadingBox
import com.aniko.ui.component.ChipRow
import com.aniko.ui.theme.AnixThemeTokens
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран «Мой профиль» (Фаза 7): аватар/логин/спонсор-бан-статусы, сетка статистики и
 * privacy-настройки. Загрузка/ошибка — тот же паттерн `AnixLoadingBox`/`AnixErrorBox`, что
 * `LibraryScreen`, только на весь экран (а не поверх уже отрисованного списка), так как
 * профиль — не пагинируемая лента, а одна карточка данных.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        val profile = uiState.profile
        val privacy = uiState.privacy

        when {
            uiState.isLoading && profile == null ->
                AnixLoadingBox(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )

            uiState.error != null && profile == null ->
                AnixErrorBox(
                    message = uiState.error?.message ?: "Не удалось загрузить профиль",
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )

            profile != null && privacy != null ->
                ProfileContent(
                    profile = profile,
                    privacy = privacy,
                    onUpdatePrivacyStats = viewModel::updatePrivacyStats,
                    onUpdatePrivacyCounts = viewModel::updatePrivacyCounts,
                    onUpdatePrivacySocial = viewModel::updatePrivacySocial,
                    onUpdatePrivacyFriendRequests = viewModel::updatePrivacyFriendRequests,
                    onToggleIncognito = viewModel::toggleIncognito,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
        }
    }
}

@Composable
private fun ProfileContent(
    profile: ProfileDetails,
    privacy: ProfilePrivacy,
    onUpdatePrivacyStats: (PrivacyVisibility) -> Unit,
    onUpdatePrivacyCounts: (PrivacyVisibility) -> Unit,
    onUpdatePrivacySocial: (PrivacyVisibility) -> Unit,
    onUpdatePrivacyFriendRequests: (FriendRequestVisibility) -> Unit,
    onToggleIncognito: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceL),
    ) {
        ProfileHeader(profile = profile)

        HorizontalDivider()

        Text(text = "Статистика", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StatsGrid(profile = profile)

        HorizontalDivider()

        Text(text = "Приватность", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PrivacySection(
            privacy = privacy,
            onUpdatePrivacyStats = onUpdatePrivacyStats,
            onUpdatePrivacyCounts = onUpdatePrivacyCounts,
            onUpdatePrivacySocial = onUpdatePrivacySocial,
            onUpdatePrivacyFriendRequests = onUpdatePrivacyFriendRequests,
            onToggleIncognito = onToggleIncognito,
        )
    }
}

/** Аватар + логин + (опционально) бейдж спонсора и баннер бана. Ничего лишнего, если аккаунт в порядке. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeader(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceS),
    ) {
        AnixAvatar(avatarUrl = profile.avatarUrl, login = profile.login, size = 96.dp)
        Text(text = profile.login, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (profile.isSponsor) {
            AssistChip(onClick = {}, label = { Text("Спонсор") })
        }

        if (profile.isBanned || profile.isPermBanned) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(dimens.spaceM)) {
                    Text(
                        text = if (profile.isPermBanned) "Аккаунт заблокирован навсегда" else "Аккаунт заблокирован",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    val banReason = profile.banReason
                    if (!banReason.isNullOrBlank()) {
                        Text(
                            text = banReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Простая сетка статистики: по 4 подписанных числа в ряд — оправданно проще `LazyVerticalGrid`
 * для 8 фиксированных полей без скролла.
 */
@Composable
private fun StatsGrid(
    profile: ProfileDetails,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val stats =
        listOf(
            "Смотрю" to profile.watchingCount,
            "В планах" to profile.planCount,
            "Просмотрено" to profile.completedCount,
            "Отложено" to profile.holdOnCount,
            "Брошено" to profile.droppedCount,
            "Избранное" to profile.favoriteCount,
            "Друзья" to profile.friendCount,
            "Комментарии" to profile.commentCount,
        )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        stats.chunked(4).forEach { rowStats ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                rowStats.forEach { (label, count) ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    privacy: ProfilePrivacy,
    onUpdatePrivacyStats: (PrivacyVisibility) -> Unit,
    onUpdatePrivacyCounts: (PrivacyVisibility) -> Unit,
    onUpdatePrivacySocial: (PrivacyVisibility) -> Unit,
    onUpdatePrivacyFriendRequests: (FriendRequestVisibility) -> Unit,
    onToggleIncognito: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        PrivacyRow(title = "Кто видит статистику", selected = privacy.stats, onSelect = onUpdatePrivacyStats)
        PrivacyRow(title = "Кто видит списки", selected = privacy.counts, onSelect = onUpdatePrivacyCounts)
        PrivacyRow(title = "Кто видит соцсети", selected = privacy.social, onSelect = onUpdatePrivacySocial)
        FriendRequestPrivacyRow(selected = privacy.friendRequests, onSelect = onUpdatePrivacyFriendRequests)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Режим инкогнито", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = privacy.isIncognito, onCheckedChange = { onToggleIncognito() })
        }
    }
}

@Composable
private fun PrivacyRow(
    title: String,
    selected: PrivacyVisibility,
    onSelect: (PrivacyVisibility) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            items = PrivacyVisibility.entries,
            isSelected = { it == selected },
            label = PrivacyVisibility::toDisplayName,
            onClick = onSelect,
        )
    }
}

@Composable
private fun FriendRequestPrivacyRow(
    selected: FriendRequestVisibility,
    onSelect: (FriendRequestVisibility) -> Unit,
) {
    val dimens = AnixThemeTokens.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
        Text(text = "Заявки в друзья", style = MaterialTheme.typography.bodyMedium)
        ChipRow(
            items = FriendRequestVisibility.entries,
            isSelected = { it == selected },
            label = FriendRequestVisibility::toDisplayName,
            onClick = onSelect,
        )
    }
}

private fun PrivacyVisibility.toDisplayName(): String =
    when (this) {
        PrivacyVisibility.EVERYONE -> "Все"
        PrivacyVisibility.FRIENDS_ONLY -> "Друзья"
        PrivacyVisibility.ONLY_ME -> "Только я"
    }

private fun FriendRequestVisibility.toDisplayName(): String =
    when (this) {
        FriendRequestVisibility.EVERYONE -> "Все"
        FriendRequestVisibility.NOBODY -> "Никто"
    }
