package com.aniko.app.feature.gallery

import androidx.lifecycle.ViewModel
import com.aniko.data.locale.LocaleStore
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel экрана-галереи дизайн-токенов (Фаза 2 плана, P2.T12).
 *
 * Единственная ответственность — язык: тема экрана переключается локальным Compose-состоянием
 * (визуальная проверка одного экрана, персистентность не нужна, см. `TokenGalleryScreen`), а
 * язык — по-настоящему через [LocaleStore] (P2.T11), чтобы галерея заодно служила живой проверкой
 * решения P2.T8 (runtime-переключение языка через Lyricist без перезапуска).
 */
class TokenGalleryViewModel(
    private val localeStore: LocaleStore,
) : ViewModel() {
    val languageTag: StateFlow<String?> = localeStore.languageTag

    fun setLanguageTag(tag: String?) {
        localeStore.setLanguageTag(tag)
    }
}
