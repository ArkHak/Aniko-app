package com.aniko.app.feature.release

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aniko.model.Episode
import com.aniko.model.EpisodeSource
import com.aniko.model.VideoHost
import com.aniko.model.VoiceType
import com.aniko.ui.component.ChipRow
import com.aniko.ui.component.EpisodeGrid
import com.aniko.ui.component.VoiceTypeRow
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.i18n.Strings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Сетка серий Title Detail (P7.T8/P8.T6, Трек C + Фаза 8): флоу выбора типа озвучки → источника →
 * [EpisodeGrid]. Обычный тап по ячейке — играть серию ([onEpisodeClick], существующая навигация
 * на плеер), долгий тап — переключить watched/unwatched ([onEpisodeLongClick], см. D4 и
 * `ReleaseDetailsViewModel.toggleWatched`).
 *
 * `state.displayEpisodes` (не `state.episodes`) — уже смерженный с локальным оверрайдом список
 * (см. [displayEpisodes] в `ReleaseDetailsContract.kt`), сюда попадает специально не сырой список
 * с сервера.
 *
 * P8.T6 (`docs/REELWAVE_PLAN.md`) заменил плоский [ChipRow] типов озвучки на [VoiceTypeRow] —
 * живая верификация (`GET episode/1`) подтвердила, что `is_sub`/`view_count`/`pinned` реально
 * приходят с сервера (см. `EpisodeMapper`), поэтому фильтр All/Dubs/Subs, бейдж SUB и счётчик
 * просмотров построены на реальных данных, а не выдуманы под мокап. Бейджа NEW нет — см. KDoc
 * [VoiceTypeRow] про то, почему это честный вырез, а не подделка.
 */
@Suppress("LongParameterList") // Координирующий блок: состояние + 2 колбэка выбора + 2 колбэка ячейки.
@Composable
fun ReleaseEpisodesSection(
    state: ReleaseDetailsUiState,
    onSelectVoiceType: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onEpisodeClick: (sourceId: Int, position: Int, host: VideoHost) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val strings = LocalStrings.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        // Track A (design-match-remaining-screens, 2026-09-04): заголовки секций внутри карточки
        // Title Detail на макете — все 14px/700 (см. тот же приём в `ReleaseHeaderSection.
        // HeroSectionTitle`/`ReleaseDetailsScreen.CommentsLinkRow`), раньше здесь был `titleMedium`
        // (16px).
        Text(
            text = strings.releaseInfoEpisodesLabel,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = SECTION_TITLE_SIZE),
            fontWeight = FontWeight.Bold,
        )

        if (state.voiceTypes.isNotEmpty()) {
            SectionLabel(strings.releaseSectionVoiceType)
            VoiceTypeSelector(
                voiceTypes = state.voiceTypes,
                selectedTypeId = state.selectedTypeId,
                onSelectVoiceType = onSelectVoiceType,
                strings = strings,
            )
        }

        if (state.sources.isNotEmpty()) {
            SectionLabel(strings.releaseSectionSource)
            ChipRow(
                items = state.sources,
                isSelected = { it.id == state.selectedSourceId },
                label = { source -> sourceLabel(source, strings) },
                onClick = { onSelectSource(it.id) },
                leadingIcon = { source -> sourceIcon(source) },
            )
        }

        when {
            // Не `AnixLoadingState.fillMaxSize()` — эта секция живёт внутри уже прокручиваемой
            // колонки с неограниченной высотой, `fillMaxSize()` там уронит layout.
            state.isEpisodesStepLoading -> CircularProgressIndicator(modifier = Modifier.padding(dimens.spaceM))

            state.episodesStepError != null ->
                Text(
                    text = state.episodesStepError.toEpisodesMessage(strings),
                    style = MaterialTheme.typography.bodyMedium,
                    // P11.T7 (Трек C): `colorScheme.error` не проходит контраст 4.5:1 как цвет
                    // текста на surface в обеих темах (особенно светлой) — `errorText` подобран
                    // именно под текст (см. KDoc `AnixColors`/`AnixPalette` в shared/ui/theme).
                    color = AnixThemeTokens.colors.errorText,
                )

            state.episodes.isNotEmpty() -> {
                SectionLabel(strings.releaseSectionEpisodesList)
                val sourceId = state.selectedSourceId
                // Хост берём из уже отображённого списка источников текущего выбора — без
                // отдельного кэша/повторного запроса, гонка состояния тут невозможна.
                val host = state.sources.firstOrNull { it.id == sourceId }?.host ?: VideoHost.UNKNOWN
                EpisodeGrid(
                    episodes = state.displayEpisodes,
                    onEpisodeClick = { episode ->
                        if (sourceId != null) onEpisodeClick(sourceId, episode.position, host)
                    },
                    currentPosition = state.release?.lastViewEpisode,
                    onEpisodeLongClick = onEpisodeLongClick,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
    )
}

private fun LoadError?.toEpisodesMessage(strings: Strings): String =
    when (this) {
        LoadError.NO_CONNECTION -> strings.commonErrorNoConnection
        LoadError.UNAUTHORIZED -> strings.commonErrorUnauthorized
        LoadError.GENERIC, null -> strings.releaseEpisodesLoadError
    }

/** Локальный клиентский фильтр списка типов озвучки (P8.T6) — не сетевой параметр. */
private enum class VoiceFilter { ALL, DUB, SUB }

/**
 * Чипы All/Dubs/Subs + список [VoiceTypeRow] (P8.T6). Фильтр и порядок — чисто клиентские:
 * закреплённые ([VoiceType.pinned]) типы озвучки поднимаются в начало (`sortedByDescending`
 * стабилен, порядок остальных не меняется), затем применяется фильтр по [VoiceType.isSub].
 * Оба поля подтверждены живым `GET episode/{releaseId}` (см. KDoc [ReleaseEpisodesSection]).
 *
 * Локальный `remember` без ключа релиза: пересоздаётся вместе со всем composable-деревом
 * [ReleaseEpisodesSection] при смене `releaseId` (экран целиком уходит из композиции при
 * навигации на другой тайтл), отдельный сброс по `state.release?.id` не нужен.
 */
@Composable
private fun VoiceTypeSelector(
    voiceTypes: List<VoiceType>,
    selectedTypeId: Int?,
    onSelectVoiceType: (Int) -> Unit,
    strings: Strings,
) {
    val dimens = AnixThemeTokens.dimens
    var filter by remember { mutableStateOf(VoiceFilter.ALL) }
    val sorted = remember(voiceTypes) { voiceTypes.sortedByDescending(VoiceType::pinned) }
    val sameCastNotes = remember(voiceTypes) { computeSameCastNotes(voiceTypes) }
    val filtered =
        when (filter) {
            VoiceFilter.ALL -> sorted
            VoiceFilter.DUB -> sorted.filter { !it.isSub }
            VoiceFilter.SUB -> sorted.filter { it.isSub }
        }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        ChipRow(
            items = VoiceFilter.entries,
            isSelected = { it == filter },
            label = { option -> option.label(strings) },
            onClick = { filter = it },
        )
        filtered.forEach { voiceType ->
            VoiceTypeRow(
                voiceType = voiceType,
                selected = voiceType.id == selectedTypeId,
                onClick = { onSelectVoiceType(voiceType.id) },
                sameCastAsName = sameCastNotes[voiceType.id],
            )
        }
    }
}

private fun VoiceFilter.label(strings: Strings): String =
    when (this) {
        VoiceFilter.ALL -> strings.filterChipAll
        VoiceFilter.DUB -> strings.releaseVoiceFilterDub
        VoiceFilter.SUB -> strings.releaseVoiceFilterSub
    }

/**
 * Заметка «тот же состав, что у X» из мокапа (P8.T6): простое сравнение непустых [VoiceType.workers]
 * между типами озвучки одного релиза — без сложной логики (нормализации порядка имён и т.п.),
 * т.к. живая верификация показала пустое `workers` у большинства типов (см. задание P8.T6).
 * Для типа с непустым `workers`, совпадающим с другим типом, возвращает имя первого совпавшего.
 */
private fun computeSameCastNotes(voiceTypes: List<VoiceType>): Map<Int, String> =
    buildMap {
        voiceTypes.forEach { type ->
            val workers = type.workers?.trim().orEmpty()
            if (workers.isEmpty()) return@forEach
            val match = voiceTypes.firstOrNull { it.id != type.id && it.workers?.trim() == workers }
            if (match != null) put(type.id, match.name)
        }
    }

/** Подпись чипа источника: имя + количество серий, если оно известно. */
private fun sourceLabel(
    source: EpisodeSource,
    strings: Strings,
): String {
    val count = source.episodesCount ?: return source.name
    return "${source.name} · ${strings.releaseEpisodesCount(count)}"
}

/**
 * Иконка хоста источника видео — универсальная (play/help), не логотип конкретного сервиса
 * (ассетов логотипов хостов в проекте нет). [VideoHost.UNKNOWN] честно деградирует до нейтральной
 * иконки-вопроса, а не показывает случайный/неверный значок (см. задание про баг `fromKey`).
 */
private fun sourceIcon(source: EpisodeSource): String =
    if (source.host == VideoHost.UNKNOWN) "help" else "play_circle"

private const val SECTION_LABEL_ALPHA = 0.6f
private val SECTION_TITLE_SIZE = 14.sp
