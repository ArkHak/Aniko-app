package com.aniko.player

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ОПЦИОНАЛЬНАЯ живая интеграция: настоящий Kodik (публичный API Anixart без токена) + настоящий libVLC.
 *
 * **По умолчанию (и на CI) тест ПРОПУСКАЕТСЯ через `Assume`** и не делает ничего: ни сети, ни libVLC —
 * `NativeDiscovery` на CI без libVLC зацикливается (PR #76, тот же довод, что у [VlcQualitySwitchIntegrationTest]).
 * Включается переменной окружения `ANIKO_LIVE_IT=1`; если libVLC на машине не найден, тест тоже пропускается.
 *
 * Переменные окружения:
 * - `ANIKO_LIVE_IT=1` — включить тест;
 * - `ANIKO_LIVE_IT_ORDER` — порядок озвучек через запятую из `anidub`, `shiza`, `subs`; по умолчанию
 *   `anidub,shiza,subs,anidub`. Загружаются только озвучки из порядка, повтор той же озвучки берёт ту же ссылку;
 * - `ANIKO_LIVE_IT_OUT` — файл, в который дописывается отчёт (то же, что печатается в консоль с префиксом `[LIVE]`).
 *
 * Запуск: `ANIKO_LIVE_IT=1 ANIKO_LIVE_IT_OUT=/tmp/live.txt ./gradlew :shared:player:desktopTest --rerun
 * --tests 'com.aniko.player.VlcLiveVoiceQualityIntegrationTest'` (`--rerun` — Gradle кэширует результат теста,
 * зависящего от окружения).
 *
 * Сценарий — цепочка «озвучка → качество → озвучка → качество…»: на каждом шаге играет очередная озвучка, а со
 * второго шага ещё и меняется качество на другое из доступных. Если у озвучки меньше двух качеств (CDN Kodik
 * иногда отдаёт меньше, чем обычно), смена качества на этом шаге пропускается с записью в отчёт, а не падает.
 *
 * Что повторяется из приложения (`EmbedPlayer.desktop.kt`, `DesktopVlcjPlayer`): плеер — тот же
 * `CallbackMediaPlayerComponent`; ссылка серии — как у `EpisodeRepository.resolveEpisodeTarget` (Kodik: URL без
 * query + Referer `anixmirai.com`); смена озвучки в приложении разбирает ВСЁ поддерево плеера (контроллер +
 * компонент) и собирает новое — здесь то же: `detach` → `stop` → `release` старого, затем новый компонент и
 * контроллер. Звук выключается (громкость 0), чтобы тест не играл аниме через динамики.
 *
 * Известные флаки внешнего CDN, не баги теста: озвучка может надолго повиснуть в буферизации (тогда падает
 * ожидание [AWAIT_TIMEOUT_MS]), а резолвер иногда успевает найти только одно качество (см. выше — пропуск).
 * Тест не может повесить сборку: watchdog жёстко завершает тестовую JVM по [HARD_DEADLINE_MS].
 *
 * Не проверяет то, чего у Compose-слоя нет в тесте: top-level окна видео/оверлея.
 */
class VlcLiveVoiceQualityIntegrationTest {
    private val enabled = System.getenv("ANIKO_LIVE_IT") == "1"

    private fun log(line: String) {
        println("[LIVE] $line")
        System.getenv("ANIKO_LIVE_IT_OUT")?.let { File(it).appendText(line + "\n") }
    }

    private fun get(url: String): JsonObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = NET_TIMEOUT_MS
        connection.readTimeout = NET_TIMEOUT_MS
        val body = connection.inputStream.bufferedReader().readText()
        return Json.parseToJsonElement(body).jsonObject
    }

    private class Voice(
        val name: String,
        val embedUrl: String,
    )

    /** Озвучка релиза 1 в API Anixart: имя для отчёта и `typeId`. */
    private class VoiceSpec(
        val name: String,
        val typeId: Int,
    )

    private fun loadVoice(spec: VoiceSpec): Voice {
        val sourceId =
            get("$API/episode/1/${spec.typeId}")
                .array("sources")
                .map { it.jsonObject }
                .first { it.string("name") == "Kodik" }
                .int("id")
        val position =
            get("$API/episode/1/${spec.typeId}/$sourceId")
                .array("episodes")
                .map { it.jsonObject }
                .first { it.string("name").startsWith("1 ") }
                .int("position")
        val url =
            get("$API/episode/target/1/$sourceId/$position")
                .getValue("episode")
                .jsonObject
                .string("url")
        return Voice(spec.name, url.substringBefore('?'))
    }

    /** Один «экземпляр плеера» = то, что приложение создаёт на каждую загрузку источника. */
    private inner class Session(
        val voice: Voice,
    ) {
        val component = CallbackMediaPlayerComponent()
        val controller = EmbedVideoController()
        val state get() = controller.state.value

        fun open() {
            val startedAt = System.currentTimeMillis()
            controller.attach(component.mediaPlayer())
            component.mediaPlayer().audio().setVolume(0)
            controller.setExpectedSource(voice.embedUrl)
            val resolved =
                checkNotNull(runBlocking { DesktopStreamResolver.resolve(voice.embedUrl, ANIX_REFERER) }) {
                    "resolve failed for ${voice.name}"
                }
            val tookMs = System.currentTimeMillis() - startedAt
            log("${voice.name}: resolved in $tookMs ms, qualities=${resolved.qualityStreams.keys}")
            resolved.qualityStreams.forEach { (quality, url) -> log("   $quality -> ${url.take(LOG_URL_CHARS)}") }
            log("   default streamUrl -> ${resolved.streamUrl.take(LOG_URL_CHARS)}")
            controller.startResolvedStream(resolved)
        }

        fun await(
            what: String,
            timeoutMs: Long = AWAIT_TIMEOUT_MS,
            condition: () -> Boolean,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(POLL_MS)
            }
            throw AssertionError("[${voice.name}] timeout waiting for: $what; state=$state")
        }

        fun awaitPlaying(minMs: Long = MIN_PLAYING_MS) =
            await("video found + playing at >= $minMs ms") {
                state.isVideoFound && state.isPlaying && state.currentTimeMs >= minMs
            }

        fun awaitAdvance(byMs: Long = MIN_ADVANCE_MS) {
            val start = state.currentTimeMs
            await("time advances by $byMs from $start") { state.isPlaying && state.currentTimeMs >= start + byMs }
        }

        /**
         * Смена качества на любое другое из доступных. Если качеств меньше двух — пропуск с записью в отчёт
         * (проблема внешнего CDN, а не смены качества), не падение.
         */
        fun switchQuality() {
            val current = state.currentQuality
            val target = state.availableQualities.firstOrNull { it != current }
            if (state.availableQualities.size < MIN_QUALITIES_TO_SWITCH || target == null) {
                log(
                    "${voice.name}: quality switch SKIPPED, need >= 2 qualities: " +
                        "current=$current available=${state.availableQualities}",
                )
                return
            }
            val before = state.currentTimeMs
            val startedAt = System.currentTimeMillis()
            controller.setQuality(target)
            await("switch $current -> $target") {
                state.switchingQualityTo == null && state.currentQuality == target && state.isPlaying
            }
            val tookMs = System.currentTimeMillis() - startedAt
            awaitAdvance()
            val after = state.currentTimeMs
            log(
                "${voice.name}: quality $current -> $target took=$tookMs ms, " +
                    "position before=$before after(+2s)=$after, failure=${state.qualitySwitchFailure}",
            )
            assertNull(state.qualitySwitchFailure, "[${voice.name}] quality switch failed")
            assertTrue(
                after >= before - POSITION_TOLERANCE_MS,
                "[${voice.name}] position jumped back: before=$before after=$after",
            )
        }

        /** Разбор плеера в том же порядке, что `DisposableEffect.onDispose` в `DesktopVlcjPlayer`. */
        fun close() {
            val startedAt = System.currentTimeMillis()
            controller.detach()
            runCatching { component.mediaPlayer().controls().stop() }
            runCatching { component.release() }
            log("${voice.name}: closed in ${System.currentTimeMillis() - startedAt} ms")
        }
    }

    private fun libVlcFound(): Boolean {
        val found = AtomicBoolean(false)
        val worker = Thread { found.set(runCatching { NativeDiscovery().discover() }.getOrDefault(false)) }
        worker.isDaemon = true
        worker.start()
        worker.join(DISCOVERY_TIMEOUT_MS)
        return found.get()
    }

    /**
     * Нативный вызов libVLC невозможно прервать из JVM, поэтому сценарий охраняет watchdog-поток: по истечении
     * [HARD_DEADLINE_MS] он жёстко завершает тестовую JVM (`Runtime.halt`) — Gradle честно падает, а не висит часами.
     */
    private fun withWatchdog(scenario: () -> Unit) {
        val watchdog =
            Thread {
                try {
                    Thread.sleep(HARD_DEADLINE_MS)
                    System.err.println("${javaClass.simpleName}: hard deadline exceeded, halting the test JVM")
                    Runtime.getRuntime().halt(WATCHDOG_EXIT_CODE)
                } catch (_: InterruptedException) {
                    // сценарий закончился вовремя
                }
            }
        watchdog.isDaemon = true
        watchdog.start()
        try {
            scenario()
        } finally {
            watchdog.interrupt()
        }
    }

    /** Порядок озвучек из `ANIKO_LIVE_IT_ORDER` (или по умолчанию); каждая озвучка загружается один раз. */
    private fun voicesInOrder(): List<Voice> {
        val keys = System.getenv("ANIKO_LIVE_IT_ORDER")?.split(',')?.map { it.trim() } ?: DEFAULT_ORDER
        val loaded = mutableMapOf<String, Voice>()
        return keys.map { key -> loaded.getOrPut(key) { loadVoice(VOICES.getValue(key)) } }
    }

    private fun playStep(
        index: Int,
        voice: Voice,
    ) {
        val session = Session(voice)
        try {
            session.open()
            session.awaitPlaying()
            session.awaitAdvance(MIN_PLAY_BEFORE_SWITCH_MS)
            val state = session.state
            log(
                "${voice.name}: step ${index + 1}: playing at ${state.currentTimeMs} ms, " +
                    "quality=${state.currentQuality}",
            )
            if (index > 0) session.switchQuality()
        } finally {
            session.close()
        }
    }

    @Test
    fun voiceAndQualitySwitchChainKeepsPlaying() {
        assumeTrue("set ANIKO_LIVE_IT=1 to run", enabled)
        assumeTrue("libVLC not found", libVlcFound())
        withWatchdog {
            // Шаг 1 — играет первая озвучка; со шага 2 к смене озвучки добавляется смена качества.
            voicesInOrder().forEachIndexed { index, voice -> playStep(index, voice) }
        }
    }

    private companion object {
        const val API = "https://api-s.anixsekai.com"
        const val ANIX_REFERER = "https://anixmirai.com/"
        const val NET_TIMEOUT_MS = 15_000
        const val AWAIT_TIMEOUT_MS = 45_000L
        const val POLL_MS = 200L
        const val MIN_PLAYING_MS = 1_500L
        const val MIN_ADVANCE_MS = 2_000L
        const val MIN_PLAY_BEFORE_SWITCH_MS = 4_000L
        const val MIN_QUALITIES_TO_SWITCH = 2
        const val POSITION_TOLERANCE_MS = 8_000L
        const val DISCOVERY_TIMEOUT_MS = 20_000L
        const val HARD_DEADLINE_MS = 8 * 60_000L
        const val WATCHDOG_EXIT_CODE = 86
        const val LOG_URL_CHARS = 140

        val VOICES =
            mapOf(
                "anidub" to VoiceSpec("AniDUB", typeId = 1),
                "shiza" to VoiceSpec("SHIZA", typeId = 17),
                "subs" to VoiceSpec("Субтитры", typeId = 24),
            )
        val DEFAULT_ORDER = listOf("anidub", "shiza", "subs", "anidub")
    }
}

// Доступ к полям ответа API: `getValue` бросает понятное исключение с именем поля вместо `!!`.
private fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray

private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
