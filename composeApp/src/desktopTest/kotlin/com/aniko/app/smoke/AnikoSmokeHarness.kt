package com.aniko.app.smoke

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.aniko.app.App
import com.aniko.app.di.appModule
import com.aniko.data.di.dataModule
import com.aniko.database.di.databaseModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * [LifecycleOwner]-заглушка для смоук-тестов.
 *
 * Продовый путь запуска [App] — `desktopMain/Main.kt` — всегда рисует его внутри Compose Desktop
 * `Window { }`, которая сама регистрирует рабочий `LocalLifecycleOwner` (RESUMED, пока окно
 * активно). [runSkikoComposeUiTest] окна не создаёт — `setContent` вызывается без него, и
 * `LocalLifecycleOwner.current` внутри теста повисает на дефолте, который никогда не движется
 * дальше `INITIALIZED`. `NavHost` (`androidx.navigation.compose`) синхронизирует lifecycle каждой
 * `NavBackStackEntry` с lifecycle хоста — если хост застрял на `INITIALIZED`, попытка теста
 * закрыть сцену (`SkikoComposeUiTest.closeScene`) на выходе пытается перевести записи бэкстека
 * сразу в `DESTROYED`, минуя `CREATED` — `LifecycleRegistry` бросает `IllegalStateException`
 * (`State must be at least 'CREATED' to be moved to 'DESTROYED'`), тест падает уже ПОСЛЕ того,
 * как все проверки [body] отработали (эта заглушка не помогает саму навигацию — с ней экраны и
 * так переключались корректно, ломался только teardown).
 *
 * Явно подставляем свой [LifecycleRegistry], доводим до [Lifecycle.State.RESUMED] перед
 * [SkikoComposeUiTest.setContent] и обратно до [Lifecycle.State.DESTROYED] сразу после [body] — то
 * есть ПОКА тест ещё владеет управлением, а не полагаемся на `closeScene`. `LifecycleRegistry`
 * сам проходит промежуточные состояния (`RESUMED → STARTED → CREATED → DESTROYED`), поэтому
 * `NavBackStackEntry` каждой записи бэкстека получает корректную последовательность событий, и
 * `closeScene` после этого просто не находит записей в некорректном состоянии.
 */
private class SmokeTestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun setState(state: Lifecycle.State) {
        registry.currentState = state
    }
}

/**
 * Корневой [ViewModelStoreOwner] смоук-теста: `runSkikoComposeUiTest` его не очищает, поэтому без
 * явной очистки ViewModel экранов переживали бы тест — их `viewModelScope` оставался бы живым на
 * протяжении всего JVM-прогона. Это ломает тесты с глобальным состоянием (например,
 * `PendingCatalogFilterLink`: «зомби»-`SearchViewModel` из прошлого теста первым подхватывает и
 * гасит ссылку, и ViewModel текущего теста её не видит). Хранилище очищается в [runAnikoSmokeTest]
 * сразу после `body`.
 */
private class SmokeTestViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * Input-сторона навигационных событий («Esc → back») для смоук-харнесса.
 *
 * На реальном desktop-окне Esc обрабатывает `ComposeSceneMediator`: `BackNavigationEventInput`
 * (ui-desktop, desktopMain) превращает его в back-событие `NavigationEventDispatcher`, до которого
 * доходит `Popup(dismissOnBackPress)`/`DropdownMenu` и закрывается. Хедлес-окно
 * `runSkikoComposeUiTest` mediator не создаёт: инжектированные через `performKeyInput` Esc
 * доходят до сфокусированного слоя (popup), но обратно в dispatcher не проваливаются — штатное
 * закрытие меню по Esc в тесте недостижимо без эмуляции этого платформенного моста. Харнесс
 * регистрирует этот input в dispatcher-е сцены (см. [rememberSmokeBackPress]) и выдаёт тестам
 * `pressBack` — событие идёт по тому же dispatcher-у, по которому на проде идёт Esc.
 */
private class SmokeBackNavigationInput : NavigationEventInput() {
    /** Диспатчит back-событие в подключённый dispatcher (аналог Esc на реальном окне). */
    fun pressBack() {
        dispatchOnBackCompleted()
    }
}

/** `LocalInternalNavigationEventDispatcherOwner` объявлен internal в ui-desktop — достаём рефлексией. */
@Suppress("UNCHECKED_CAST")
private val internalNavigationEventDispatcherOwnerLocal: CompositionLocal<NavigationEventDispatcherOwner> =
    Class
        .forName("androidx.compose.ui.platform.DefaultNavigationEventDispatcherOwner_skikoKt")
        .getDeclaredField("LocalInternalNavigationEventDispatcherOwner")
        .apply { isAccessible = true }
        .get(null) as CompositionLocal<NavigationEventDispatcherOwner>

/**
 * Создаёт [SmokeBackNavigationInput], привязанный к dispatcher-у навигационных событий, который
 * тестовая сцена провайдит в композицию (`Popup` регистрирует в нём же свой back-обработчик).
 */
@Composable
private fun rememberSmokeBackPress(): () -> Unit {
    val input = remember { SmokeBackNavigationInput() }
    val owner = internalNavigationEventDispatcherOwnerLocal.current
    LaunchedEffect(owner) {
        owner.navigationEventDispatcher.addInput(input)
    }
    return input::pressBack
}

/**
 * `androidx.lifecycle.MainDispatcherChecker` (используется `LifecycleRegistry.setCurrentState`
 * на JVM/desktop-таргете) кеширует РЕАЛЬНЫЙ AWT/Swing event-dispatch поток как "главный" при
 * первом обращении (`Dispatchers.Main.immediate` внутри `runBlocking` — на desktop это
 * `Dispatchers.Swing`, всегда исполняется на настоящем AWT EDT независимо от вызывающего потока).
 * [runSkikoComposeUiTest], в отличие от продового `Window { }`, гоняет ВСЮ композицию — включая
 * обработку кликов и последующую навигацию (`NavController.navigate`, которая синхронно двигает
 * lifecycle затронутых `NavBackStackEntry`) — на потоке тестового coroutine-планировщика, а не на
 * AWT EDT. `NavBackStackEntry` создаёт свой lifecycle-registry с включённой проверкой главного
 * потока (без публичного способа её отключить снаружи), поэтому любая навигация внутри
 * `runSkikoComposeUiTest` валится с `IllegalStateException: Method setCurrentState must be called
 * on the main thread` — не баг приложения, а несовместимость `androidx.navigation-compose`
 * (ожидает реальный UI-поток) с headless-архитектурой Compose Multiplatform desktop-тестов
 * (намеренно однопоточна на потоке теста, без запуска реального AWT event loop).
 *
 * Обходится точечно: `isMainDispatcherAvailable` — приватное статическое поле, определяющее,
 * нужна ли вообще эта проверка (`false` → `isMainDispatcherThread()` всегда `true`, без сравнения
 * потоков). В однопоточном по построению тесте (весь тест физически исполняется одним потоком за
 * раз, гонки в принципе невозможны) отключение проверки безопасно — она защищает от реальных
 * многопоточных гонок в проде, которых здесь нет и быть не может.
 */
private fun disableLifecycleMainThreadEnforcement() {
    val checkerClass = Class.forName("androidx.lifecycle.MainDispatcherChecker")
    val field = checkerClass.getDeclaredField("isMainDispatcherAvailable")
    field.isAccessible = true
    field.setBoolean(null, false)
}

/**
 * Дефолтный размер виртуального окна для смоук-тестов — узкий (compact) по ширине.
 *
 * `runComposeUiTest` (без явного `size`) на десктопе даёт окно ~1024×768px, что при
 * `AnixWindowSize.MEDIUM_MIN_DP = 600` резолвится в `Medium`/`Expanded` — тогда каркас рисует
 * `NavigationRail`/постоянный sidebar вместо `NavigationBar`, а `AnixTestTags.bottomNavItem`
 * (единственные тегированные элементы навигации, F4 фундамента) просто не существуют в дереве.
 * 390×844 — примерный компакт-профиль телефона, надёжно ниже границы 600dp при density=1f.
 */
private val COMPACT_WINDOW_SIZE = Size(390f, 844f)

// Compose UI smoke-testing harness для composeApp (F2/F3, Фаза 11 — `docs/REELWAVE_PLAN.md`).
//
// Лежит в `desktopTest`, а НЕ в `commonTest`, сознательно: `runComposeUiTest`
// (`org.jetbrains.compose.ui:ui-test`, каталожный accessor `compose.uiTest`) из `commonTest`
// компилируется и в `androidUnitTest` — а без Robolectric он там падает на старте (нет реального
// Android-окружения). CI гоняет `desktopTest` + `testDebugUnitTest` одной командой, поэтому
// `androidUnitTest`, ловящий такой падающий тест, был бы неприятным сюрпризом. `desktopTest`
// компилируется только под JVM-таргет `desktop` — там `runComposeUiTest` работает "как есть"
// (Skiko headless-рендерer), без JUnit4-правила.
//
// Сами сценарии смоук-тестов (собственно `@Test`-функции с `onNodeWithTag`/`onNodeWithText` и
// т.п.) — задача других треков Фазы 11, НЕ этого файла. Здесь только инфраструктура запуска.

/**
 * No-op [ImageLoader] для смоук-тестов — без network-фетчера. `components { }` пустой: Coil не
 * найдёт фетчер для `http(s)://` URL и просто не загрузит картинку (постер останется плейсхолдером
 * `AsyncImage`/`onError`) — смоук-тест не проверяет пиксели постеров, поэтому это не потеря.
 * Альтернатива — тянуть реальный `KtorNetworkFetcherFactory` поверх [fakeApiEngine] — сознательно
 * не сделана: URL постеров (`s.anixmirai.com/...`) не входят в [defaultFixtureRoutes], а заводить
 * под них ещё и бинарные JPEG-фикстуры — за пределами фундамента F1-F4.
 */
fun noOpImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context).components { }.build()

/**
 * Запускает граф [App] внутри [runComposeUiTest] поверх фейковой инфраструктуры.
 *
 * Поднимает Koin с реальными `databaseModule`/`dataModule`/`appModule` (композApp-вьюмодели) — то
 * есть настоящая бизнес-логика, — но [fakeInfraModule] в конце списка переопределяет всё, что
 * трогало бы сеть/диск/системные сервисы (см. её KDoc). Подменяет Coil-синглтон на
 * [noOpImageLoader]. Каждый вызов стартует и останавливает СВОЙ экземпляр Koin (`startKoin`/
 * `stopKoin` в `finally`) — сценарии из одного тестового класса, если их несколько, не должны
 * запускаться параллельно друг с другом (обычный порядок JUnit — последовательный — этому не
 * противоречит).
 *
 * @param apiRoutes см. [fakeInfraModule].
 * @param initialToken см. [fakeInfraModule] — `null` (по умолчанию) означает, что граф стартует
 * с экрана логина.
 * @param koinDeclaration точка расширения для конкретного сценария (например, свой `single<X>`
 * поверх дефолтного графа) — применяется ПОСЛЕ [fakeInfraModule], поэтому переопределяет её.
 * @param windowSize размер виртуального окна в px при `density = 1f` (то есть в dp) — по умолчанию
 * [COMPACT_WINDOW_SIZE] (телефон). Меняется для сценариев, зависящих от `AnixWindowSize`: широкое
 * окно (`Expanded`, >840dp) рисует постоянный сайдбар вместо `NavigationBar`, и навигация в таких
 * сценариях ищется по видимому тексту лейбла, а не по [com.aniko.ui.testing.AnixTestTags.bottomNavItem].
 * @param body тело теста — обычный код [SkikoComposeUiTest] (`onNodeWithTag`, `onNodeWithText`, …).
 * Приёмник — конкретный класс [SkikoComposeUiTest], а не интерфейс `ComposeUiTest`: последний не
 * умеет снимать пиксели (`captureToImage()` для скриншот-проверок раскладки объявлен именно на
 * классе, `ComposeUiTest.skiko.kt`), а все боевые сценарии Фазы 11 пользуются только общими
 * методами интерфейса, поэтому сужение приёмника ничего не ломает.
 * @param pressBack (первый параметр лямбды) эмуляция Esc на реальном окне: диспатчит back-событие
 * в dispatcher навигационных событий сцены, так что `Popup`/`DropdownMenu`/`BackHandler`
 * отрабатывают как на проде. Платформенная конвертация Esc → back живёт в `ComposeSceneMediator`
 * (desktopMain) и в headless-окне теста не существует — см. KDoc [SmokeBackNavigationInput].
 */
@OptIn(ExperimentalTestApi::class)
fun runAnikoSmokeTest(
    apiRoutes: Map<String, () -> String> = emptyMap(),
    initialToken: String? = null,
    koinDeclaration: KoinAppDeclaration? = null,
    windowSize: Size = COMPACT_WINDOW_SIZE,
    body: SkikoComposeUiTest.(pressBack: () -> Unit) -> Unit,
) {
    startKoin {
        modules(databaseModule, dataModule, appModule, fakeInfraModule(apiRoutes, initialToken))
        koinDeclaration?.invoke(this)
    }
    disableLifecycleMainThreadEnforcement()
    val lifecycleOwner = SmokeTestLifecycleOwner()
    val viewModelStoreOwner = SmokeTestViewModelStoreOwner()
    try {
        SingletonImageLoader.setSafe { context -> noOpImageLoader(context) }
        runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
            lifecycleOwner.setState(Lifecycle.State.RESUMED)
            lateinit var pressBack: () -> Unit
            setContent {
                pressBack = rememberSmokeBackPress()
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycleOwner,
                    LocalViewModelStoreOwner provides viewModelStoreOwner,
                ) {
                    App()
                }
            }
            body(pressBack)
            lifecycleOwner.setState(Lifecycle.State.DESTROYED)
            viewModelStoreOwner.viewModelStore.clear()
        }
    } finally {
        stopKoin()
    }
}
