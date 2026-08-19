package com.aniko.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import cafe.adriel.lyricist.LanguageTag
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings

/**
 * Корень i18n-дерева (Фаза 2 плана, P2.T7/P2.T8/P2.T9): подключает [LocalStrings] через
 * Lyricist поверх текущего выбора языка.
 *
 * **Почему Lyricist, а не Compose Multiplatform Resources** (полное обоснование —
 * `docs/REELWAVE_PLAN.md`, P2.T7/P2.T8): спайк подтвердил, что в CMP 1.11.1 нет публичного
 * способа переопределить локаль ресурсов в рантайме — `ComposeEnvironment`/
 * `LocalComposeEnvironment`/`ResourceEnvironment` и конструкторы `LanguageQualifier`/
 * `RegionQualifier` помечены `internal`/`@InternalResourceApi`, попытка сослаться на
 * `LocalComposeEnvironment` из другого модуля не компилируется («Cannot access ... it is
 * internal in file»). `getString(environment, resource)` — единственная публичная non-Composable
 * альтернатива — тоже не помогает: `ResourceEnvironment` нельзя сконструировать самому
 * (`internal constructor`), можно только прочитать системный через `getSystemResourceEnvironment()`.
 * Lyricist, наоборот, построен вокруг мутабельного `Lyricist.languageTag` (`StateFlow` внутри) —
 * смена языка обычным присваиванием триггерит рекомпозицию всего поддерева без перезапуска
 * процесса, что и требовалось доказать в P2.T8.
 *
 * [languageTag] — `null` означает «следовать системной локали» ([Locale.current]); при
 * системной локали без перевода (не `en`/`ru`) Lyricist сам фолбэкается на `defaultLanguageTag`
 * (`"en"`, см. `Lyricist.getStrings`) — краша на незнакомой локали нет.
 *
 * Живёт в `shared/ui` (не в пакете `shared/ui/theme` — он уже смержен и не редактируется по
 * заданию Фазы 2), а не в `shared/data`, потому что зависит от Compose Runtime/Lyricist и подключает
 * `CompositionLocal` — то же обоснование, что у существующих [LocalAnixColors]/[LocalAnixDimens].
 * Персистентный выбор языка ([com.aniko.data.locale.LocaleStore], `shared/data`, P2.T11) читается
 * на уровне `composeApp` (там уже есть Koin) и передаётся сюда параметром — `shared/ui` ничего не
 * знает про DI/Settings, только про Compose-механику переключения.
 */
@Composable
fun ProvideAppStrings(
    languageTag: String?,
    content: @Composable () -> Unit,
) {
    val currentLanguageTag = languageTag ?: Locale.current.language
    val lyricist =
        rememberStrings(
            translations = appTranslations,
            defaultLanguageTag = DEFAULT_LANGUAGE_TAG,
            currentLanguageTag = currentLanguageTag,
        )
    ProvideStrings(lyricist, LocalStrings, content)
}

/**
 * Тот же выбор перевода, что делает [ProvideAppStrings], но БЕЗ композиции (P10.T6).
 *
 * Нужен там, где текст строится вне UI-дерева и его некому получить из [LocalStrings]: локальные
 * OS-уведомления собираются в фоновом тике синхронизации — на Android это `CoroutineWorker` в
 * процессе вообще без Activity, никакой композиции там нет и не будет.
 *
 * Правила разрешения повторяют композабл-версию один в один, чтобы уведомление не оказалось на
 * другом языке, чем приложение: [languageTag] `null` → системная локаль ([Locale.current]),
 * незнакомый язык → `en`. Регион отбрасывается (`ru-RU` → `ru`) — переводы заведены по языку.
 */
fun appStringsFor(languageTag: String?): Strings {
    val tag = languageTag ?: Locale.current.language
    val language = tag.substringBefore('-').substringBefore('_').lowercase()
    return appTranslations[language] ?: appTranslations.getValue(DEFAULT_LANGUAGE_TAG)
}

private const val DEFAULT_LANGUAGE_TAG: LanguageTag = "en"

private val appTranslations: Map<LanguageTag, Strings> =
    mapOf(
        "en" to EnStrings,
        "ru" to RuStrings,
    )
