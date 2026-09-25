package com.aniko.app.feature.update

/** Действия диалога обновления — одним объектом, чтобы не раздувать списки параметров. */
internal class UpdateDialogActions(
    val onInstall: () -> Unit,
    val onLater: () -> Unit,
    val onSkip: () -> Unit,
    val onCancel: () -> Unit,
)
