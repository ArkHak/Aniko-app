package com.aniko.data.update

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Итог одной проверки. */
sealed interface UpdateCheckResult {
    /** Проверка пропущена: с прошлой ещё не прошло [UpdateChecker.minInterval] (только автоматическая). */
    data object Throttled : UpdateCheckResult

    /** Новее нет (в том числе когда репозиторий закрыт и релизов не видно). */
    data class UpToDate(
        val current: AppVersion,
    ) : UpdateCheckResult

    /** Есть версия новее, но пользователь нажал «Пропустить» (только автоматическая проверка). */
    data class Skipped(
        val release: AppRelease,
    ) : UpdateCheckResult

    data class Available(
        val release: AppRelease,
    ) : UpdateCheckResult

    data class Failed(
        val error: UpdateError,
    ) : UpdateCheckResult
}

/**
 * Решает, есть ли версия новее [currentVersion].
 *
 * Автоматическая проверка ([force] = `false`) не чаще [minInterval] и уважает «пропущенную»
 * версию; ручная ([force] = `true`) игнорирует и то и другое. Никогда не предлагает версию
 * ≤ текущей (защита от даунгрейда). Берётся максимальная по SemVer среди всех опубликованных релизов.
 */
class UpdateChecker(
    private val source: ReleaseSource,
    private val store: UpdateStore,
    private val currentVersion: AppVersion,
    private val nowMs: () -> Long,
    val minInterval: Duration = 24.hours,
) {
    suspend fun check(force: Boolean): UpdateCheckResult {
        val now = nowMs()
        if (!force && now - store.lastCheckAtMs < minInterval.inWholeMilliseconds) return UpdateCheckResult.Throttled
        return when (val fetched = fetch()) {
            is Fetched.Failure -> resultOfFailure(fetched.error, now)
            is Fetched.Releases -> {
                store.lastCheckAtMs = now
                pickBest(fetched.list, force)
            }
        }
    }

    /** «Пропустить эту версию»: автоматическая проверка больше не предложит именно её (новее — предложит). */
    fun skip(version: AppVersion) {
        store.skippedVersion = version.toString()
    }

    private suspend fun fetch(): Fetched =
        try {
            Fetched.Releases(source.fetchReleases())
        } catch (e: UpdateException) {
            Fetched.Failure(e.error)
        }

    /**
     * Репозиторий закрыт/релизов нет — для пользователя это «обновлений нет», и долбить GitHub каждый
     * запуск незачем; остальные сбои окно троттлинга не начинают.
     */
    private fun resultOfFailure(
        error: UpdateError,
        now: Long,
    ): UpdateCheckResult =
        if (error == UpdateError.NotFound) {
            store.lastCheckAtMs = now
            UpdateCheckResult.UpToDate(currentVersion)
        } else {
            UpdateCheckResult.Failed(error)
        }

    private fun pickBest(
        releases: List<AppRelease>,
        force: Boolean,
    ): UpdateCheckResult {
        val best = releases.maxByOrNull { it.version }?.takeIf { it.version > currentVersion }
        return when {
            best == null -> UpdateCheckResult.UpToDate(currentVersion)
            !force && store.skippedVersion == best.version.toString() -> UpdateCheckResult.Skipped(best)
            else -> UpdateCheckResult.Available(best)
        }
    }

    private sealed interface Fetched {
        class Releases(
            val list: List<AppRelease>,
        ) : Fetched

        class Failure(
            val error: UpdateError,
        ) : Fetched
    }
}
