package com.aniko.app.di

import com.aniko.data.di.dataModule
import com.aniko.data.session.SecureTokenStorage
import com.aniko.network.ApiConfig
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Предрелизный аудит безопасности (issue #109, п. 1): `ApiConfig` (а с ним флаг HTTP-логирования)
 * поставляет `appModule`, а не `dataModule`. Смоук-харнесс подменяет `HttpClient` фейком, поэтому
 * продовое определение `HttpClient` -> `ApiConfig` там не резолвится вовсе — этот тест собирает
 * настоящий граф (без сети) и ловит потерянный/дублирующийся биндинг `ApiConfig`.
 */
class ApiConfigWiringTest {
    private fun startKoin(): KoinApplication =
        koinApplication {
            modules(
                dataModule,
                appModule,
                module {
                    single<Settings> { MapSettings() }
                    single<SecureTokenStorage> {
                        object : SecureTokenStorage {
                            override suspend fun get(): String? = null

                            override suspend fun set(token: String) = Unit

                            override suspend fun clear() = Unit
                        }
                    }
                },
            )
        }

    @Test
    fun apiConfig_loggingFollowsBuildType() {
        val koinApp = startKoin()
        try {
            val config = koinApp.koin.get<ApiConfig>()

            assertEquals(isDebugBuild(), config.enableLogging)
            // Тесты идут не из установленного `.app` (нет свойства jpackage.app-path) — это debug.
            assertTrue(config.enableLogging, "Desktop вне jpackage-приложения должен считаться debug-сборкой")
        } finally {
            koinApp.close()
        }
    }

    @Test
    fun realHttpClient_isResolvableFromTheProductionGraph() {
        val koinApp = startKoin()
        try {
            val client = koinApp.koin.get<HttpClient>()

            assertNotNull(client)
            client.close()
        } finally {
            koinApp.close()
        }
    }
}
