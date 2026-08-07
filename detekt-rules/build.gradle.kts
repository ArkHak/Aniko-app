/**
 * Модуль кастомного detekt-правила (P2.T10, `docs/REELWAVE_PLAN.md`) — против захардкоженных
 * кириллических строковых литералов вне i18n-слоя.
 *
 * ВАЖНО про границу includeBuild — почему этот модуль объявлен в КОРНЕВОМ `settings.gradle.kts`
 * (`include(":detekt-rules")`), а НЕ в `build-logic/settings.gradle.kts` (эмпирически проверено,
 * см. журнал изменений плана): `build-logic` (см. `pluginManagement { includeBuild("build-logic")
 * }` в корневом `settings.gradle.kts`) — отдельная Gradle-сборка со своим project-графом. Функция
 * `project(path)` резолвит `path` относительно project-графа ТОГО `Project`, на котором она
 * вызвана в рантайме — а это всегда проект-потребитель (`:composeApp`, `:shared:*`), часть
 * КОРНЕВОЙ сборки, вне зависимости от того, в каком физическом файле лежит вызывающий код. Если
 * бы `:detekt-rules` был объявлен внутри build-logic, `project(":detekt-rules")`, вызванный на
 * любом проекте корневой сборки, не нашёл бы его — граф build-logic и граф корневой сборки не
 * пересекаются.
 *
 * Из этого же факта следует НЕочевидный вывод (тоже проверен эмпирически, см. `aniko.lint.
 * gradle.kts`): раз `project(...)` резолвится относительно проекта-получателя, а не относительно
 * места, где текстуально написан код, — совершенно неважно, что `detektPlugins(project(":detekt-
 * rules"))` объявлен внутри precompiled script plugin `aniko.lint.gradle.kts`, СКОМПИЛИРОВАННОГО
 * как часть build-logic: Kotlin DSL precompiled script plugin компилируется в класс
 * `Plugin<Project>`, чьё тело выполняется как `apply(target: Project)` — и `project(...)` внутри
 * этого тела это `target.project(...)`, вызванный на РЕАЛЬНОМ объекте-получателе (`:shared:model`
 * и т.п., часть корневой сборки). Проверка: временно добавил `dependencies { add("detektPlugins",
 * project(":detekt-rules")) }` прямо в `aniko.lint.gradle.kts`, убрал любое упоминание
 * `detektPlugins` из `shared/model/build.gradle.kts`, добавил туда временный кириллический
 * литерал и прогнал `:shared:model:detektMetadataCommonMain` — правило сработало. Значит ОДНОЙ
 * декларации в convention-плагине достаточно для ВСЕХ потребителей `aniko.lint` разом — повторять
 * `detektPlugins(project(":detekt-rules"))` в каждом из `composeApp`/`shared:model`/`shared:
 * network`/`shared:data`/`shared:player`/`shared:ui` не нужно (см. `aniko.lint.gradle.kts`).
 *
 * Обычный Kotlin/JVM модуль (не KMP) — detekt-правила пишутся и выполняются на JVM независимо
 * от таргетов анализируемого кода. Плагин `aniko.jvm` (не `alias(libs.plugins.kotlin.jvm)`
 * напрямую) — см. его KDoc в build-logic: применение "голого" Kotlin/JVM плагина здесь грузит
 * второй экземпляр Kotlin Gradle Plugin другим class loader'ом (эмпирически подтверждённое
 * предупреждение Gradle "loaded multiple times in different subprojects... may break the
 * build").
 */
plugins {
    id("aniko.jvm")
}

dependencies {
    // compileOnly: сама detekt-api (и её транзитивные зависимости — kotlin-compiler-embeddable
    // и т.п.) уже присутствуют в classpath detekt-таски у потребителя в рантайме (приносит сам
    // detekt Gradle-плагин) — упаковывать их в наш плагин-jar не нужно и вредно (конфликт версий).
    compileOnly(libs.detekt.api)
}
