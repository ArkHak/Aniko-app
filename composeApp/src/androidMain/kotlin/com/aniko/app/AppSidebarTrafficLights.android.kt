package com.aniko.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android: оконного хрома нет, сайдбар на этой платформе не используется — пустой actual. */
@Composable
actual fun AppSidebarTrafficLights(modifier: Modifier) = Unit
