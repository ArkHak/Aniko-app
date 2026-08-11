package com.aniko.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import com.aniko.model.VideoHost

/**
 * Единственное, что экраны знают про навигацию к тайтлу/комментариям/плееру — они НЕ знают, идёт
 * ли переход через `NavController` (compact — полноэкранный маршрут) или через [DetailPaneStack]
 * (wide — persistent-панель рядом со списком, P5.T3). Эту развилку решает реализация
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
 * Реализация [TitleNavigator] поверх `NavController` (compact) + [DetailPaneStack] (wide).
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
        )
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
