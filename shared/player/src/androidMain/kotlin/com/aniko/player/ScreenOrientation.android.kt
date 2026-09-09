package com.aniko.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * `LocalContext.current` на Compose Multiplatform внутри Android-хоста — почти всегда обёртка
 * над `Activity` (`ContextThemeWrapper` и т.п.), а не сама `Activity` — `as? Activity` напрямую
 * почти всегда возвращает `null`. Разворачиваем цепочку `ContextWrapper.baseContext`, пока не
 * найдём настоящую `Activity` или не упрёмся в `Application`/`null` (тогда честно `null` — нечем
 * управлять, вызывающая сторона просто не блокирует ориентацию).
 */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Процесс-уровневое состояние политики ориентации.
 *
 * Политика захвата/освобождения: при переходе 0→1 фиксируется **эффективная** ориентация
 * устройства (значение `Configuration.orientation` до того, как мы начнём форсировать landscape),
 * а не только `requestedOrientation`. При освобождении 1→0, если исходный `requestedOrientation`
 * был явным нормальным значением (не `UNSPECIFIED` и не landscape-лок), он восстанавливается
 * сразу. Если же приложение жило на `UNSPECIFIED` (типовой случай), мы форсируем
 * *входную* ориентацию (`SENSOR_PORTRAIT` или `SENSOR_LANDSCAPE`) на короткое settle-окно,
 * а затем возвращаем `UNSPECIFIED`. Это гарантирует детерминированный возврат к той ориентации,
 * в которой пользователь зашёл в плеер, и не оставляет экран случайно в landscape из-за датчика.
 *
 * Зачем реф-каунт, а не просто `original` в каждом [DisposableEffect] (наивная версия):
 * 2026-09-08 в проде жил баг «после закрытия видео из горизонтального положения приложение
 * остаётся в горизонтали». Корень — дублирующийся экран плеера: быстрый двойной тап по
 * «Смотреть»/серии или гонка навигации пушили второй Player-маршрут поверх первого, и пока шёл
 * дефолтный 700ms-fade NavHost, в композиции одновременно жили два экземпляра этого эффекта
 * (см. журнал; фикс дублей — мгновенные переходы маршрута + `popUpTo<Player>` в
 * `TitleNavigator.openPlayer`). В наивной версии каждый инстанс захватывал `original` в момент
 * своего старта: второй инстанс стартовал уже после того, как первый записал
 * `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, поэтому его `original` == landscape-лок, и при закрытии
 * последним диспоузился именно второй инстанс, «восстанавливая» landscape — экран навсегда
 * оставался горизонтальным.
 *
 * Здесь `baseRequestedOrientation` и `baseEffectiveOrientation` фиксируются один раз — только
 * при переходе 0→1 активных блокировок (пока ориентация ещё честно «до плеера»), а
 * восстанавливаются на 1→0. Landscape-значение в `baseRequestedOrientation` невозможно даже
 * теоретически: первый инстанс всегда стартует до того, как кто-либо записал landscape-лок.
 * Всё же страхуемся на случай постороннего локера (другая Activity/окно): при restore
 * landscape-значение из «базы» игнорируется и путь идёт через force-match effective → settle.
 *
 * Re-enter внутри settle-окна: `baseRequestedOrientation`/`baseEffectiveOrientation`
 * сбрасываются только когда отложенный runnable действительно выполнился и вернул
 * `requestedOrientation` к базе. Пока runnable ещё не сработал, повторный захват 0→1
 * видит уже зафиксированную базу и не перезаписывает её текущим `requestedOrientation`.
 * Это предотвращает ситуацию, когда наш собственный settle-force (`SENSOR_PORTRAIT` или
 * `SENSOR_LANDSCAPE`) ошибочно воспринимается как внешний осознанный лок и "восстанавливается"
 * навсегда — обе оси при быстром повторном входе корректно возвращаются к `UNSPECIFIED`.
 */
private var activeLandscapeLocks = 0
private var baseRequestedOrientation: Int? = null
private var baseEffectiveOrientation: Int? = null
private var pendingRelease: Runnable? = null
private val mainHandler = Handler(Looper.getMainLooper())

private const val SETTLE_MS = 400L

private fun cancelPendingRelease() {
    pendingRelease?.let(mainHandler::removeCallbacks)
    pendingRelease = null
}

/**
 * `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, не жёсткий `LANDSCAPE` — даёт устройству выбирать между
 * `LandscapeLeft`/`LandscapeRight` по датчику (пользователь может держать телефон камерой влево
 * или вправо, оба варианта нормальны для видео), но не даёт провалиться обратно в portrait.
 * `configChanges="orientation|..."` на `MainActivity` (`AndroidManifest.xml`) уже гарантирует, что
 * Activity не пересоздастся, когда мы сами меняем `requestedOrientation` — иначе весь экран плеера
 * потерял бы состояние ровно в момент вызова этой функции.
 */
@Composable
actual fun LockLandscapeOrientationEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity != null) {
            cancelPendingRelease()
            if (activeLandscapeLocks == 0 && baseRequestedOrientation == null) {
                // Не перезаписываем базу, если она уже захвачена и мы всё ещё внутри
                // settle-окна (см. KDoc процесс-уровневого состояния выше).
                baseRequestedOrientation = activity.requestedOrientation
                baseEffectiveOrientation = activity.resources.configuration.orientation
            }
            activeLandscapeLocks++
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (activity != null) {
                activeLandscapeLocks--
                if (activeLandscapeLocks == 0) {
                    val restoreRequested = baseRequestedOrientation
                    val restoreEffective = baseEffectiveOrientation
                    cancelPendingRelease()

                    val explicitSafe =
                        restoreRequested != null &&
                            restoreRequested != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                            restoreRequested !in LANDSCAPE_LOCK_VALUES

                    if (explicitSafe) {
                        // Внешний осознанный лок: восстанавливаем сразу, базу можно сбросить.
                        baseRequestedOrientation = null
                        baseEffectiveOrientation = null
                        activity.requestedOrientation = restoreRequested
                    } else {
                        // settle-force + отложенный возврат к базе. База остаётся живой,
                        // чтобы re-enter внутри settle-окна не захватил наш собственный force.
                        when (restoreEffective) {
                            Configuration.ORIENTATION_PORTRAIT -> {
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                scheduleReleaseToUnspecified(activity, restoreRequested)
                            }

                            Configuration.ORIENTATION_LANDSCAPE -> {
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                scheduleReleaseToUnspecified(activity, restoreRequested)
                            }

                            else -> {
                                baseRequestedOrientation = null
                                baseEffectiveOrientation = null
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Откладывает возврат к базовому `requestedOrientation` на [SETTLE_MS], чтобы система успела
 * применить форсированную «входную» ориентацию. Захватывает конкретный [Activity], отменяется
 * при любом новом 0→1 через [cancelPendingRelease], и не выполняет запись, если Activity уже
 * завершается или уничтожена. По выполнении сбрасывает `baseRequestedOrientation` и
 * `baseEffectiveOrientation`, завершая settle-окно.
 */
private fun scheduleReleaseToUnspecified(
    activity: Activity,
    base: Int?,
) {
    val runnable =
        Runnable {
            pendingRelease = null
            if (activity.isFinishing || activity.isDestroyed) {
                baseRequestedOrientation = null
                baseEffectiveOrientation = null
                return@Runnable
            }
            activity.requestedOrientation = base ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            baseRequestedOrientation = null
            baseEffectiveOrientation = null
        }
    pendingRelease = runnable
    mainHandler.postDelayed(runnable, SETTLE_MS)
}

private val LANDSCAPE_LOCK_VALUES =
    setOf(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
    )
