package com.aniko.app.feature.player

import androidx.lifecycle.viewModelScope
import com.aniko.app.smoke.fakeInfraModule
import com.aniko.app.smoke.fixtures.ApiFixtures
import com.aniko.data.di.dataModule
import com.aniko.data.playerposition.PositionKey
import com.aniko.data.repository.EpisodeRepository
import com.aniko.database.di.databaseModule
import com.aniko.model.VideoHost
import com.aniko.player.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Настоящий [PlayerViewModel] поверх настоящих репозиториев и фикстурного API (тот же приём, что у
 * смоук-харнесса, но без UI): релиз 1 с двумя озвучками на Kodik — AniDUB (`typeId=1`, `sourceId=8`)
 * и «Субтитры» (`typeId=24`, `sourceId=24`); ссылки серии подставлены свои, чтобы по URL было видно,
 * какой источник загружен. Сеть — только [io.ktor.client.engine.mock.MockEngine].
 *
 * Регрессия: после `selectVoiceType` маршрут экрана остаётся прежним, и повторный `load(маршрут)` (экран
 * покинул композицию и вошёл снова — поворот/ресайз, KDoc `PlayerUiState.isFullscreen`) откатывал плеер
 * к исходной озвучке. Ожидания построены на достижении состояния (`awaitState`), а не на моменте вызова:
 * ответы фикстурного API приходят с другого потока, и «сразу после `load` идёт загрузка» — гонка.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelVoiceSwitchTest {
    private class Fixture(
        /** Ссылка серии 1 озвучки Б — параметризована ради [nextEpisodeHostFollowsLoadedVoiceNotRoute]:
         *  дефолт (kodikplayer.com) резолвится в тот же [VideoHost.KODIK], что и маршрутный `host`,
         *  и не показал бы регрессию, где `host` для «Следующей серии» берётся из параметра
         *  маршрута, а не из реально загруженного источника. */
        private val voiceBUrl: String = URL_B,
    ) {
        val hitsVoiceA = AtomicInteger()
        val hitsVoiceB = AtomicInteger()

        @Volatile var voiceAFails = false

        @Volatile var voiceBFails = false

        val routes: Map<String, () -> String> =
            mapOf(
                "episode/1" to { ApiFixtures.p131Types },
                "episode/1/1" to { ApiFixtures.p131SourcesType1Anidub },
                "episode/1/24" to { ApiFixtures.p131SourcesType24Subs },
                "episode/1/1/8" to { ApiFixtures.p131EpisodesType1Source8 },
                "episode/1/24/24" to { ApiFixtures.p131EpisodesType24Source24 },
                "episode/target/1/8/1" to { target(hitsVoiceA, 1, URL_A, voiceAFails) },
                "episode/target/1/8/2" to { target(null, 2, URL_A_NEXT, fails = false) },
                "episode/target/1/24/1" to { target(hitsVoiceB, 1, voiceBUrl, voiceBFails) },
            )

        /** Ответ `episode/target`: считает обращения и, если [fails], падает как недоступный источник. */
        private fun target(
            hits: AtomicInteger?,
            position: Int,
            url: String,
            fails: Boolean,
        ): String {
            hits?.incrementAndGet()
            check(!fails) { "источник недоступен: $url" }
            return """{"code":0,"episode":{"position":$position,"name":"$position серия","url":"$url","iframe":true}}"""
        }
    }

    /** Поднимает Koin с настоящей бизнес-логикой и фикстурным API, отдаёт ViewModel и гасит всё в конце. */
    private fun withPlayerViewModel(
        fixture: Fixture = Fixture(),
        body: (PlayerViewModel, Fixture) -> Unit,
    ) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val koin =
            startKoin { modules(databaseModule, dataModule, fakeInfraModule(fixture.routes)) }.koin
        val viewModel =
            PlayerViewModel(
                episodeRepository = koin.get<EpisodeRepository>(),
                libraryRepository = koin.get(),
                positionStore = koin.get(),
                titleVoicePreferenceStore = koin.get(),
            )
        try {
            body(viewModel, fixture)
        } finally {
            viewModel.viewModelScope.cancel()
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    private fun PlayerViewModel.awaitState(predicate: (PlayerUiState) -> Boolean): PlayerUiState =
        runBlocking { withTimeout(AWAIT_TIMEOUT_MS) { uiState.first(predicate) } }

    /** Серия загружена и озвучка подобрана — можно переключать. */
    private fun PlayerViewModel.awaitLoaded(): PlayerUiState =
        awaitState { !it.isLoading && it.source != null && it.currentVoiceType != null }

    private val PlayerViewModel.embedUrl: String?
        get() = (uiState.value.source as? PlaybackSource.Embed)?.url

    @Test
    fun repeatedRouteLoadAfterVoiceSwitchKeepsSelectedVoice() =
        withPlayerViewModel { vm, fixture ->
            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)
            vm.awaitLoaded()
            assertEquals(URL_A, vm.embedUrl)

            vm.selectVoiceType(VOICE_B_TYPE)
            vm.awaitState { !it.isLoading && (it.source as? PlaybackSource.Embed)?.url == URL_B }
            assertEquals(PositionKey(RELEASE, VOICE_B_SOURCE, ROUTE_POSITION), vm.uiState.value.positionKey)
            val voiceAHits = fixture.hitsVoiceA.get()

            // Экран покинул композицию и вошёл снова: LaunchedEffect зовёт load с ИСХОДНЫМ ключом маршрута.
            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)

            // Перезагрузка сбросила бы состояние (загрузка → озвучка А) — озвучка Б остаётся, А не запрашивается.
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(URL_B, vm.embedUrl)
            assertEquals(PositionKey(RELEASE, VOICE_B_SOURCE, ROUTE_POSITION), vm.uiState.value.positionKey)
            assertEquals(voiceAHits, fixture.hitsVoiceA.get())
        }

    /**
     * Регрессия `PlayerScreen.openNextEpisode` (2026-09-22): раньше «Следующая серия» брала `host`
     * из ПАРАМЕТРА МАРШРУТА (`hostKey`/`host` экрана) — он не пересчитывается при смене озвучки, и
     * после [PlayerViewModel.selectVoiceType] кнопка звала бы `navigator.openPlayer` со старым
     * хостом. Экран теперь берёт хост из `(state.source as PlaybackSource.Embed).host` — того же
     * поля, что заполняет [EpisodeRepository.resolveEpisodeTarget] при (пере)загрузке, — эта
     * проверка через [PlayerUiState] и есть источник данных для той кнопки.
     *
     * Озвучка Б здесь — на `sibnet.ru` (домен, который резолвится в другой [VideoHost]), а не
     * дефолтный kodikplayer.com фикстуры: иначе домен обеих озвучек резолвился бы в один и тот же
     * [VideoHost.KODIK], что и маршрутный `host`, и тест не отличил бы старое (сломанное) поведение
     * от нового.
     */
    @Test
    fun nextEpisodeHostFollowsLoadedVoiceNotRoute() =
        withPlayerViewModel(Fixture(voiceBUrl = URL_B_SIBNET)) { vm, _ ->
            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)
            vm.awaitLoaded()
            assertEquals(VideoHost.KODIK, (vm.uiState.value.source as PlaybackSource.Embed).host)

            vm.selectVoiceType(VOICE_B_TYPE)
            val loaded = vm.awaitState { !it.isLoading && (it.source as? PlaybackSource.Embed)?.url == URL_B_SIBNET }

            // Реально загруженный источник теперь на Sibnet — «Следующая серия» обязана взять
            // именно этот хост, а не VideoHost.KODIK, с которым экран был открыт изначально.
            assertEquals(VideoHost.SIBNET, (loaded.source as PlaybackSource.Embed).host)
            assertEquals(VOICE_B_SOURCE, loaded.positionKey?.sourceId)
        }

    @Test
    fun nextEpisodeRouteKeyStillLoads() =
        withPlayerViewModel { vm, _ ->
            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)
            vm.awaitLoaded()

            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION + 1, VideoHost.KODIK)

            vm.awaitState { !it.isLoading && (it.source as? PlaybackSource.Embed)?.url == URL_A_NEXT }
            assertEquals(PositionKey(RELEASE, ROUTE_SOURCE, ROUTE_POSITION + 1), vm.uiState.value.positionKey)
        }

    @Test
    fun sameRouteKeyAfterErrorLoadsAgain() =
        withPlayerViewModel(Fixture().apply { voiceAFails = true }) { vm, fixture ->
            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)
            vm.awaitState { it.error != null }
            fixture.voiceAFails = false

            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)

            // Из ошибки в загруженное состояние можно попасть только перезагрузкой.
            vm.awaitState { !it.isLoading && (it.source as? PlaybackSource.Embed)?.url == URL_A }
        }

    @Test
    fun retryReloadsCurrentVoiceNotTheRoute() =
        withPlayerViewModel(Fixture().apply { voiceBFails = true }) { vm, fixture ->
            vm.load(RELEASE, ROUTE_SOURCE, ROUTE_POSITION, VideoHost.KODIK)
            vm.awaitLoaded()
            vm.selectVoiceType(VOICE_B_TYPE)
            vm.awaitState { it.error != null }
            val voiceAHits = fixture.hitsVoiceA.get()
            val voiceBHits = fixture.hitsVoiceB.get()

            vm.retry()

            // Повтор перезагружает ТЕКУЩУЮ (упавшую) озвучку Б, а не исходную по маршруту.
            vm.awaitState { it.error != null }
            assertEquals(voiceBHits + 1, fixture.hitsVoiceB.get())
            assertEquals(voiceAHits, fixture.hitsVoiceA.get())

            fixture.voiceBFails = false
            vm.retry()
            vm.awaitState { !it.isLoading && (it.source as? PlaybackSource.Embed)?.url == URL_B }
            assertEquals(voiceAHits, fixture.hitsVoiceA.get())
        }

    private companion object {
        const val RELEASE = 1
        const val ROUTE_SOURCE = 8
        const val ROUTE_POSITION = 1
        const val VOICE_B_TYPE = 24
        const val VOICE_B_SOURCE = 24
        const val AWAIT_TIMEOUT_MS = 10_000L
        const val URL_A = "https://kodikplayer.com/seria/1001/aaaaaaaa/720p"
        const val URL_A_NEXT = "https://kodikplayer.com/seria/1002/aaaaaaab/720p"
        const val URL_B = "https://kodikplayer.com/seria/2001/bbbbbbbb/720p"

        /** Домен sibnet.ru — резолвится в другой [VideoHost], см. KDoc
         *  [nextEpisodeHostFollowsLoadedVoiceNotRoute]. */
        const val URL_B_SIBNET = "https://video.sibnet.ru/shell.php?videoid=4242424"
    }
}
