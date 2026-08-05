package com.anixkmp.app.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anixkmp.ui.component.AnixEmptyBox

/** Заглушка экрана «Мои списки» — наполняется в фазе «списки». */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    AnixEmptyBox(message = "Списки появятся здесь", modifier = modifier)
}
