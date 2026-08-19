package com.aniko.ui.share

import androidx.compose.runtime.Composable

/**
 * Платформенный буфер обмена (P10.T9) — только копирование текста, без родного шер-диалога
 * (см. [ShareController] для второй половины — открытия системного UI "поделиться").
 */
interface ClipboardController {
    fun copyText(text: String)
}

/**
 * Итог вызова [ShareController.shareText] — экран сам решает, что показать пользователю по итогу
 * (see `ReleaseHeaderSection.WatchAndFavoriteRow`, composeApp): нативный шер-диалог сам покажет
 * системный UI и больше ничего от экрана не требует, а Desktop-фоллбэк без снекбара выглядел бы
 * как "кнопка ничего не сделала".
 */
enum class ShareResult {
    /** Открыт нативный системный шер-диалог (Android `Intent.createChooser`/iOS `UIActivityViewController`). */
    SHEET_PRESENTED,

    /** Нативного шер-диалога общего назначения нет (Desktop, см. `Sharing.desktop.kt`) — текст
     * скопирован в буфер вместо него, экран обязан сам показать уведомление об этом. */
    COPIED_TO_CLIPBOARD,
}

/** Системный шер-диалог (текст/ссылка) — на Desktop фоллбэк на копирование, см. [ShareResult]. */
interface ShareController {
    fun shareText(
        text: String,
        subject: String? = null,
    ): ShareResult
}

/**
 * Контроллеры привязаны к жизненному циклу композиции — тот же приём, что у
 * `rememberEmbedVideoController` (`shared/player`): Android читает `LocalContext.current` внутри
 * factory-функции, поэтому им нужен `@Composable`-контекст, а не голый no-arg конструктор — иначе
 * Android-контекст было бы негде взять без протаскивания его через Koin в каждую платформу ради
 * двух простых операций.
 */
@Composable
expect fun rememberClipboardController(): ClipboardController

@Composable
expect fun rememberShareController(): ShareController
