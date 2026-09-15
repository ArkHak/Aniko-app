plugins {
    id("aniko.kmp.library")
    id("aniko.kmp.compose")
    id("aniko.lint")
}

android {
    namespace = "com.aniko.player"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            // P8: только ради `WebViewCompat.addDocumentStartJavaScript` /
            // `addWebMessageListener` — единственного способа достучаться до `<video>`
            // в cross-origin подфрейме embed-страницы (см. `EmbedVideoBridge.kt`).
            implementation(libs.androidx.webkit)
            // WindowInsetsControllerCompat — скрытие системных панелей в fullscreen-плеере
            // (ревью замечание #3, `HideSystemBars.android.kt`).
            implementation(libs.androidx.core.ktx)
        }

        desktopMain.dependencies {
            // libVLC-биндинг — рендерер видео (Step 2/3 пересмотра P8.T1). `implementation`, не
            // `api` — наружу не торчит ни одного типа uk.co.caprica.
            implementation(libs.vlcj)
            // Пересмотр `feature/desktop-video-player` (см. журнал `docs/REELWAVE_PLAN.md`):
            // headless JCEF ([DesktopStreamResolver] раньше сниффил сетевой трафик реальным
            // Chromium) заменён на прямой HTTP-парсинг embed-страниц (Kodik `/ftor`, Sibnet
            // редиректы, AniLibria HTML) — тот же подход, что у эталонного клиента
            // github.com/Maks1mio/anixapp (`electron/kodik-direct.js`,
            // `electron/lib/direct-video-link.js`). Быстрее (секунды вместо ~15 с таймаута) и без
            // риска нативного краша JVM, который headless JCEF уже давал на этой ветке. `jcefmaven`
            // и весь модуль `DesktopWebEngine` удалены как мёртвый код — ничего в модуле больше не
            // грузит embed-страницу через браузерный движок.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            // Только `JsonElement`/`JsonObject` навигация рантаймом (Kodik `/ftor` отдаёт плоский
            // JSON `{"links": {...}}`) — без `@Serializable`-классов и без подключения
            // kotlin("plugin.serialization") в этом модуле, он здесь не нужен.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
