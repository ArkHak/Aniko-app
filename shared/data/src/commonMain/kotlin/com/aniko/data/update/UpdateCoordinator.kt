package com.aniko.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Состояние обновления, общее для диалога и строки в настройках. Без готовых текстов: i18n — на экране. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** Ручная проверка: новее нет. */
    data class UpToDate(
        val current: AppVersion,
    ) : UpdateState

    data class Available(
        val release: AppRelease,
    ) : UpdateState

    /** @param progress доля 0..1; `null` — размер неизвестен. */
    data class Downloading(
        val release: AppRelease,
        val progress: Float?,
    ) : UpdateState

    data class Installing(
        val release: AppRelease,
    ) : UpdateState

    /** Android: нужно разрешить Aniko устанавливать приложения, затем нажать «Обновить» ещё раз. */
    data class NeedsInstallPermission(
        val release: AppRelease,
    ) : UpdateState

    /** @param release `null` — сбой ручной проверки (релиз неизвестен). */
    data class Failed(
        val error: UpdateError,
        val release: AppRelease?,
    ) : UpdateState
}

/** Разовые действия для UI. */
sealed interface UpdateEffect {
    data class OpenUrl(
        val url: String,
    ) : UpdateEffect

    /** macOS: замена запущена — завершить процесс, дальше перезапуск сделает отдельный скрипт. */
    data object ExitApplication : UpdateEffect
}

/**
 * Единственный владелец процесса обновления на уровне приложения (Koin `single`): проверка,
 * загрузка с проверкой SHA-256, установка. Диалог и строка «Проверить обновления» в настройках —
 * два наблюдателя одного [state], поэтому они не расходятся и не запускают проверку дважды.
 *
 * [promptOpen] — показывать ли диалог. Он открывается, когда автоматическая проверка нашла версию,
 * и когда пользователь запустил ручную; «Позже» закрывает диалог, а [state] остаётся `Available`,
 * так что в настройках видно, что обновление ждёт.
 */
@Suppress("TooManyFunctions") // публичные интенты + мелкие шаги конвейера одного процесса; резать по границе — хуже
class UpdateCoordinator(
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
    private val installer: AppUpdateInstaller,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    private val mutablePromptOpen = MutableStateFlow(false)
    val promptOpen: StateFlow<Boolean> = mutablePromptOpen.asStateFlow()

    private val effectChannel = Channel<UpdateEffect>(Channel.BUFFERED)
    val effects: Flow<UpdateEffect> = effectChannel.receiveAsFlow()

    /** `true` — «Обновить» скачает и поставит сам; `false` — только откроет страницу релиза. */
    val canInstallInApp: Boolean = installer.capability == UpdateCapability.InAppInstall

    private var job: Job? = null

    private val isBusy: Boolean
        get() =
            when (mutableState.value) {
                is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> true
                else -> false
            }

    /**
     * Тихая проверка при запуске: не чаще раза в сутки, ошибки не показывает, диалог — только если
     * есть новая версия.
     */
    fun checkOnStart() {
        if (isBusy) return
        job =
            scope.launch {
                val result = safeCheck(force = false)
                if (result is UpdateCheckResult.Available) {
                    mutableState.value = UpdateState.Available(result.release)
                    mutablePromptOpen.value = true
                }
            }
    }

    /** Ручная проверка из настроек: игнорирует троттлинг и «пропущенную» версию; результат виден всегда. */
    fun checkNow() {
        if (isBusy) return
        mutableState.value = UpdateState.Checking
        job =
            scope.launch {
                when (val result = safeCheck(force = true)) {
                    is UpdateCheckResult.Available -> {
                        mutableState.value = UpdateState.Available(result.release)
                        mutablePromptOpen.value = true
                    }
                    is UpdateCheckResult.Failed -> mutableState.value = UpdateState.Failed(result.error, null)
                    is UpdateCheckResult.UpToDate -> mutableState.value = UpdateState.UpToDate(result.current)
                    else -> mutableState.value = UpdateState.Failed(UpdateError.Unknown, null)
                }
            }
    }

    /** «Обновить»: скачать → проверить SHA-256 → поставить (либо открыть страницу релиза, если иначе нельзя). */
    fun startUpdate() {
        val release = releaseOf(mutableState.value) ?: return
        if (isBusy) return
        mutablePromptOpen.value = true
        job = scope.launch { runUpdate(release) }
    }

    /** Снова показать диалог (тап по строке «Доступна версия X» в настройках после «Позже»). */
    fun openPrompt() {
        if (releaseOf(mutableState.value) != null) mutablePromptOpen.value = true
    }

    /** «Позже»: закрыть диалог; версия остаётся доступной в настройках. Загрузку не прерывает. */
    fun later() {
        mutablePromptOpen.value = false
        if (mutableState.value is UpdateState.Failed) mutableState.value = UpdateState.Idle
    }

    /** «Пропустить эту версию»: автоматическая проверка больше не предложит именно её. */
    fun skipVersion() {
        val release = (mutableState.value as? UpdateState.Available)?.release ?: return
        checker.skip(release.version)
        mutablePromptOpen.value = false
        mutableState.value = UpdateState.Idle
    }

    /** Отмена идущей загрузки; недокачанный файл удаляется загрузчиком. */
    fun cancel() {
        val current = mutableState.value
        job?.cancel()
        job = null
        releaseOf(current)?.let { mutableState.value = UpdateState.Available(it) }
    }

    private suspend fun runUpdate(release: AppRelease) {
        if (installer.capability == UpdateCapability.OpenReleasePage) {
            effectChannel.send(UpdateEffect.OpenUrl(release.pageUrl))
            mutablePromptOpen.value = false
        } else {
            mutableState.value = runCatching { downloadAndInstall(release) }.getOrElse { failureState(it, release) }
        }
    }

    private suspend fun downloadAndInstall(release: AppRelease): UpdateState {
        val asset = release.assetFor(installer.platform) ?: throw UpdateException(UpdateError.NoAssetForPlatform)
        mutableState.value = UpdateState.Downloading(release, null)
        val checksums = downloader.fetchChecksums(release)
        val expected = checksums[asset.name] ?: throw UpdateException(UpdateError.ChecksumMissing)
        val file = downloader.download(asset, expected, installer.workDirectory, progressReporter(release))
        mutableState.value = UpdateState.Installing(release)
        return applyOutcome(installer.install(file, release), release)
    }

    /** Обновляет прогресс не чаще, чем меняется целый процент. */
    private fun progressReporter(release: AppRelease): (Float?) -> Unit {
        var lastPercent = -1
        return { progress ->
            val percent = progress?.let { (it * PERCENT).toInt() } ?: -1
            if (percent != lastPercent) {
                lastPercent = percent
                mutableState.value = UpdateState.Downloading(release, progress)
            }
        }
    }

    private suspend fun applyOutcome(
        outcome: InstallOutcome,
        release: AppRelease,
    ): UpdateState =
        when (outcome) {
            InstallOutcome.SystemInstallerLaunched -> {
                mutablePromptOpen.value = false
                UpdateState.Available(release)
            }
            InstallOutcome.Restarting -> {
                effectChannel.send(UpdateEffect.ExitApplication)
                UpdateState.Installing(release)
            }
            InstallOutcome.PermissionRequired -> UpdateState.NeedsInstallPermission(release)
            is InstallOutcome.Failed -> UpdateState.Failed(outcome.error, release)
        }

    /** Отмена пробрасывается; типизированный сбой — как есть; неожиданный — [UpdateError.Unknown]. */
    private fun failureState(
        cause: Throwable,
        release: AppRelease,
    ): UpdateState =
        when (cause) {
            is CancellationException -> throw cause
            is UpdateException -> UpdateState.Failed(cause.error, release)
            else -> UpdateState.Failed(UpdateError.Unknown, release)
        }

    private fun releaseOf(state: UpdateState): AppRelease? =
        when (state) {
            is UpdateState.Available -> state.release
            is UpdateState.Downloading -> state.release
            is UpdateState.Installing -> state.release
            is UpdateState.NeedsInstallPermission -> state.release
            is UpdateState.Failed -> state.release
            else -> null
        }

    /** [UpdateChecker] ловит свои ошибки сам; неожиданный сбой не должен ронять приложение фоновой корутиной. */
    private suspend fun safeCheck(force: Boolean): UpdateCheckResult =
        runCatching { checker.check(force) }.getOrElse {
            if (it is CancellationException) throw it
            UpdateCheckResult.Failed(UpdateError.Unknown)
        }

    private companion object {
        const val PERCENT = 100
    }
}
