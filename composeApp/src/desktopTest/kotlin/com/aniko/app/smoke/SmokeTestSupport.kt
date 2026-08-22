package com.aniko.app.smoke

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * [KoinAppDeclaration] для [runAnikoSmokeTest] (P11.T2-T5), форсирующий английскую локаль.
 *
 * Без этого `ProvideAppStrings` резолвит язык из системной локали JVM ([androidx.compose.ui.text.intl.Locale.current]
 * → `java.util.Locale.getDefault()`), которая не контролируется тестом (зависит от машины/CI) —
 * сценарии, ищущие узлы по видимому тексту UI-строк (`onNodeWithText(strings.titleDetailWatch)` и
 * т.п.), были бы флаки на любой машине не с EN-локалью. Сам контент карточек релизов (заголовки
 * из фикстур) языконезависим — это только про `Strings`/`LocalStrings`.
 *
 * `LocaleStore` (см. `shared/data/.../locale/LocaleStore.kt`) читает выбор языка из ключа
 * `"locale.language_tag"` в [Settings] — тот же [Settings], который [fakeInfraModule] уже
 * регистрирует пустым; этот модуль идёт ПОСЛЕ него в списке `modules(...)` (`koinDeclaration`
 * применяется в [runAnikoSmokeTest] именно так), поэтому переопределяет `single<Settings>`.
 */
fun forceEnglishLocale(): KoinAppDeclaration =
    {
        modules(
            module {
                single<Settings> {
                    MapSettings().apply { putString("locale.language_tag", "en") }
                }
            },
        )
    }
