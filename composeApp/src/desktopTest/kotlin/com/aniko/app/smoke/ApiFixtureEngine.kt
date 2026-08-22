package com.aniko.app.smoke

import com.aniko.app.smoke.fixtures.ApiFixtures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * Маршруты, для которых в `docs/api/samples/` есть образец ответа (см. [ApiFixtures] —
 * сгенерирован задачей `generateSmokeApiFixtures`, `composeApp/build.gradle.kts`, из тех же
 * файлов, что и `com.aniko.data.fixtures.ApiFixtures` в `:shared:data`).
 *
 * Ключ — путь запроса БЕЗ ведущего `/` и БЕЗ query-параметров, ровно как его строят методы
 * `com.aniko.data.api.*` (`client.get("schedule")`, `client.post("discover/interesting")` и т.п.
 * — см. `EpisodeApi`/`ReleaseApi`/`ScheduleApi` в `:shared:data`).
 *
 * Намеренно НЕ покрыты параметризованные по неизвестным id эндпоинты цепочки плеера
 * (`episode/{releaseId}/{typeId}`, `episode/{releaseId}/{typeId}/{sourceId}`,
 * `episode/target/{releaseId}/{sourceId}/{position}`) — какой именно `typeId`/`sourceId` окажется
 * в пути, зависит от того, что домен-модель извлечёт из ответа `episode/{releaseId}` (шаг 1), а
 * это решение вне области фундамента F1-F4. Сценарий, которому нужна вся цепочка плеера, добавляет
 * недостающие маршруты сам через параметр `apiRoutes` (см. [fakeApiEngine]/`fakeInfraModule`),
 * например, используя [ApiFixtures.episodeSources186TypeId]/[ApiFixtures.episodeList1861Sibnet]/
 * [ApiFixtures.episodeTarget186Sibnet] под свои конкретные id.
 */
val defaultFixtureRoutes: Map<String, () -> String> =
    mapOf(
        "schedule" to { ApiFixtures.schedule },
        "discover/interesting" to { ApiFixtures.discoverInteresting },
        "discover/watching/0" to { ApiFixtures.discoverWatchingPage0 },
        "release/186" to { ApiFixtures.release186Extended },
        "search/releases/0" to { ApiFixtures.searchReleasesPage0NoApiVersionHeader },
        "filter/0" to { ApiFixtures.filterPage0 },
        "episode/186" to { ApiFixtures.episodeTypes186 },
        "release/comment/all/186/0" to { ApiFixtures.releaseCommentAll186Page0 },
    )

/**
 * [MockEngine] для смоук-тестов composeApp (F3, Фаза 11) — отдаёт зафиксированные в JSON-файлах
 * `docs/api/samples` ответы вместо реальной сети.
 *
 * Матчинг — точный, по пути без query-параметров. Путь, которого нет ни в [routes], ни в
 * [defaultFixtureRoutes], получает `HTTP 404` с телом `{"code":-1}` — а не бросает исключение при
 * построении движка: конкретный репозиторий сам решает, как обработать неожиданный ответ (обычно
 * это Error-состояние экрана), и так поведение теста ближе к тому, с чем реально столкнётся
 * пользователь при неизвестном пути, чем падение на этапе конфигурации.
 *
 * @param routes Дополнительные/переопределяющие маршруты конкретного сценария — накладываются
 * поверх [defaultFixtureRoutes] (совпадающий ключ здесь побеждает).
 */
fun fakeApiEngine(routes: Map<String, () -> String> = emptyMap()): MockEngine {
    val allRoutes = defaultFixtureRoutes + routes
    return MockEngine { request ->
        val path = request.url.encodedPath.removePrefix("/")
        val body = allRoutes[path]?.invoke()
        if (body != null) {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        } else {
            respond(
                content = """{"code":-1}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }
}
