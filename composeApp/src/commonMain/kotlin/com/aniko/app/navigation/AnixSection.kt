package com.aniko.app.navigation

/**
 * Пять секций адаптивного каркаса (P5.T5, трек A) — bottom bar (phone) / nav rail (tablet) /
 * постоянный sidebar (desktop) переключаются по одному и тому же списку секций, различается только
 * визуальное представление. Список секций и их иконки/лейблы для каждого size class собирает
 * интегратор (шаг 6) вместе с `AdaptiveScaffold` трека A — здесь только сопоставление секция →
 * маршрут, без UI.
 *
 * Порядок и состав (P13.T1/T2 плана, сверка с мокапом Claude Design): `Home → Search → Library →
 * Schedule → Profile`, порядок объявления здесь = порядок вкладок в UI ([toNavItem] в `App.kt`
 * строит список из `entries` без отдельной сортировки). Раньше было `Home, Search, Schedule,
 * Library, Settings` — мокап рисует `Главная → Каталог → Мои списки → Расписание → Профиль`,
 * поэтому `Schedule`/`Library` поменялись местами, а последняя вкладка теперь открывает
 * [AnixDestination.Profile] напрямую (person-иконка), а не [AnixDestination.Settings] (шестерёнка).
 * `Settings` остаётся отдельным маршрутом графа, но больше не входит в таб-бар — теперь это
 * дочерний экран, открываемый шестерёнкой из `TopAppBar` `ProfileScreen.kt` (см. её KDoc).
 *
 * Константа `Search` НЕ переименована в `Catalog`, хотя её лейбл в UI сменился на «Каталог»
 * ([Strings.navCatalog]) — `.name` этой константы используется как id таб-элемента
 * (`AdaptiveNavItem.id` → `AnixTestTags.bottomNavItem(id)`), и `SearchFilterSmokeTest.kt` ищет узел
 * по тегу `bottomNavItem("Search")`. Переименование константы сломало бы этот smoke-тест без
 * какой-либо функциональной пользы — расхождение между именем Kotlin-константы и видимым текстом
 * такое же, как уже было допущено у `Library`, чей лейбл стал «Мои списки», а не «Library».
 */
enum class AnixSection(
    val destination: AnixDestination,
) {
    Home(AnixDestination.Home),
    Search(AnixDestination.Search),
    Library(AnixDestination.Library),
    Schedule(AnixDestination.Schedule),
    Profile(AnixDestination.Profile),
}
