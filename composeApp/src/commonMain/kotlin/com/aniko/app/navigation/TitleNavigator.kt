package com.aniko.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import com.aniko.model.VideoHost

/**
 * Единственное, что экраны знают про навигацию к тайтлу/комментариям/плееру — они НЕ знают, идёт
 * ли переход через `NavController` (Compact/Medium — полноэкранный маршрут) или через
 * [DetailPaneStack] (Expanded — правый ящик 520dp поверх списка в `ListDetailHost`,
 * desktop-проход 2026-09-15, мокап `showDetail`; до него с P5.T3 здесь была persistent-панель
 * material3-adaptive рядом со списком). Эту развилку решает реализация
 * ([AdaptiveTitleNavigator]) по единственному входу — `isTwoPane: () -> Boolean`.
 *
 * Предоставляется через [LocalTitleNavigator] на уровне `AnixAppScaffold` (интегратор, шаг 6) —
 * экраны читают `LocalTitleNavigator.current`, а не принимают `NavController`/`DetailPaneStack`
 * напрямую в параметрах (иначе пришлось бы прокидывать флаг two-pane в каждый экран вручную).
 */
@Stable
interface TitleNavigator {
    fun openTitle(releaseId: Int)

    fun openComments(releaseId: Int)

    /** Плеер всегда полноэкранный маршрут через `NavController`, независимо от size class — он не
     * часть pane-системы ни на одном размере окна. */
    fun openPlayer(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    )

    /** `true` — было что закрыть (панель или маршрут — конкретно что, решает реализация по
     * `isTwoPane()`); `false` — сам navigator обработать "назад" не смог, разберись выше (например,
     * выйти из приложения/уйти на предыдущий таб). */
    fun back(): Boolean
}

val LocalTitleNavigator =
    staticCompositionLocalOf<TitleNavigator> {
        // Технический текст для разработчика (падение при неправильной сборке дерева), не
        // пользовательский UI-текст — намеренно на английском, как и AnixError.message
        // (см. её KDoc), а не через Strings/i18n-слой.
        error("LocalTitleNavigator is not provided — wrap in CompositionLocalProvider at AnixAppScaffold level")
    }

/**
 * Реализация [TitleNavigator] поверх `NavController` (Compact/Medium) + [DetailPaneStack]
 * (Expanded — ящик `ListDetailHost`, `isTwoPane` в `App.kt` true только на нём).
 *
 * `isTwoPane` — лямбда, а не готовое `Boolean`, и читается заново при каждом вызове
 * [openTitle]/[openComments]/[back] — так навигатор всегда видит АКТУАЛЬНый size class на момент
 * клика. [rememberTitleNavigator] намеренно НЕ оборачивает создание в `remember`: создавать новый
 * `AdaptiveTitleNavigator` при каждой рекомпозиции дёшево (3 поля-ссылки, без побочных эффектов),
 * зато исключает целый класс багов с протухшей замкнутой лямбдой, которые дал бы `remember` с
 * неполным списком ключей (`isTwoPane` меняется чаще, чем `navController`/`paneStack`).
 */
@Stable
class AdaptiveTitleNavigator(
    private val navController: NavController,
    private val paneStack: DetailPaneStack,
    private val isTwoPane: () -> Boolean,
) : TitleNavigator {
    override fun openTitle(releaseId: Int) {
        if (releaseId <= 0) return
        if (isTwoPane()) {
            paneStack.open(DetailPaneRoute.Details(releaseId))
        } else {
            navController.navigate(AnixDestination.ReleaseDetails(releaseId))
        }
    }

    override fun openComments(releaseId: Int) {
        if (releaseId <= 0) return
        if (isTwoPane()) {
            paneStack.open(DetailPaneRoute.Comments(releaseId))
        } else {
            navController.navigate(AnixDestination.ReleaseComments(releaseId))
        }
    }

    override fun openPlayer(
        releaseId: Int,
        sourceId: Int,
        position: Int,
        host: VideoHost,
    ) {
        navController.navigate(
            AnixDestination.Player(
                releaseId = releaseId,
                sourceId = sourceId,
                position = position,
                hostKey = host.key,
            ),
        ) {
            // Ровно ОДИН Player в back stack, в любой момент (фикс «два плеера дублируются и
            // накладываются», 2026-09-08, см. журнал). Без этого каждый вызов — быстрый двойной
            // тап по серии/«Смотреть», авто-переход «следующая серия», гонка deep-link с тапом —
            // пушил НОВЫЙ entry поверх старого: пока шёл дефолтный 700ms fade NavHost, оба
            // экрана плеера (каждый со своим WebView/видео) были одновременно в композиции и
            // рисовались друг на друге; navigate/back внутри этого окна мог и вовсе оставить
            // старый Player висеть в композиции навсегда («ghost» — тот же механизм, что держал
            // экран в landscape после закрытия видео, см. KDoc LockLandscapeOrientationEffect).
            // `popUpTo<Player> { inclusive = true }` снимает существующий Player ДО пуша нового:
            // быстрый двойной тап схлопывается в один entry, а «следующая серия» ЗАМЕНЯЕТ
            // текущий entry (не копится стек серий) — «назад» из любой серии возвращает на тот
            // экран, откуда плеер открывали (детали/список), а не в предыдущую серию.
            popUpTo<AnixDestination.Player> { inclusive = true }
            launchSingleTop = true
        }
    }

    override fun back(): Boolean {
        if (isTwoPane() && !paneStack.isEmpty) {
            return paneStack.pop()
        }
        return navController.popBackStack()
    }
}

/**
 * Название `remember*` — по конвенции остального проекта (см. [rememberDetailPaneStack],
 * `rememberAnixWindowSize`), не потому что тут реально используется `remember` (см. KDoc
 * [AdaptiveTitleNavigator], почему нет — `isTwoPane` протухла бы).
 */
@Composable
fun rememberTitleNavigator(
    navController: NavController,
    paneStack: DetailPaneStack,
    isTwoPane: () -> Boolean,
): TitleNavigator = AdaptiveTitleNavigator(navController, paneStack, isTwoPane)
