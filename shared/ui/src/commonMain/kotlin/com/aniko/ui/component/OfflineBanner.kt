package com.aniko.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import com.aniko.ui.i18n.LocalStrings
import com.aniko.ui.theme.AnixThemeTokens

/**
 * Глобальный индикатор офлайн-режима (P10.T3).
 *
 * **Полоса поверх всего приложения, а не снекбар/диалог.** Отсутствие сети — состояние, а не
 * событие: снекбар исчез бы через несколько секунд, и пользователь, вернувшийся к приложению
 * позже, снова не понимал бы, почему ничего не грузится. Диалог блокировал бы работу с уже
 * закэшированным контентом, которая как раз офлайн и остаётся доступной (cache-first, Фаза 4).
 *
 * **Раздвигает контент, а не накрывает его** ([AnimatedVisibility] с `expandVertically`): наложение
 * поверх скрыло бы верхнюю часть любого экрана, включая шапку с кнопкой «назад».
 *
 * Цвета — токены [com.aniko.ui.theme.AnixColors] `warning`/`onWarning`, а не `error`: связи нет —
 * это не поломка приложения, и красный здесь читался бы как сбой.
 *
 * Для скринридера баннер — `liveRegion`: появление озвучивается само, без фокуса на нём, а
 * заголовок с описанием склеиваются в одну реплику (иначе фокус пришлось бы вести по трём
 * отдельным узлам, включая ничего не значащую иконку).
 *
 * @param visible показывать ли баннер. Осознанно `Boolean`, а не `ConnectivityStatus`: `:shared:ui`
 * не зависит от `:shared:data` и не должен знать про источник статуса — решение «`Unknown` это не
 * офлайн» принимается на стороне вызывающего (см. `ConnectivityStatus.isOffline`).
 */
@Composable
fun AnixOfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = AnixThemeTokens.dimens
    val colors = AnixThemeTokens.colors
    val strings = LocalStrings.current
    val announcement = "${strings.offlineBannerTitle}. ${strings.offlineBannerDescription}"

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space12),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.warning)
                    // Баннер стоит самым первым элементом окна, выше системного каркаса, поэтому
                    // безопасную зону статус-бара он обязан обходить сам.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = dimens.spaceM, vertical = dimens.space12)
                    .clearAndSetSemantics {
                        contentDescription = announcement
                        liveRegion = LiveRegionMode.Polite
                    },
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = colors.onWarning,
                modifier = Modifier.size(dimens.spaceL),
            )
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                Text(
                    text = strings.offlineBannerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onWarning,
                )
                Text(
                    text = strings.offlineBannerDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onWarning,
                )
            }
        }
    }
}
