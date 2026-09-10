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
            // Локальный сервер (`KodikProxyServer.kt`) — отдаёт статическую HTML-обёртку с
            // `<iframe src="реальный URL источника">`. Только эта обёртка нужна, чтобы Kodik
            // прошёл свою проверку isIframe() (см. KDoc класса) — самого содержимого страницы
            // сервер не трогает, поэтому HTTP-клиент здесь не нужен, только сервер.
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
    }
}
