package com.aniko.data.update

import okio.Path

/** Что платформа умеет сделать с новой версией. */
enum class UpdateCapability {
    /** Скачать, проверить и поставить из приложения (Android — системная установка, macOS — замена `.app`). */
    InAppInstall,

    /** Только сообщить о версии и открыть страницу релиза (iOS: неподписанный `.ipa` сам себя не обновит). */
    OpenReleasePage,
}

/** Итог попытки установки проверенного файла. */
sealed interface InstallOutcome {
    /** Android: системный установщик показан, дальше решает пользователь. */
    data object SystemInstallerLaunched : InstallOutcome

    /** macOS: замена запущена в отдельном процессе — приложению нужно завершиться и перезапуститься. */
    data object Restarting : InstallOutcome

    /** Android: сперва нужно разрешить Aniko ставить приложения; экран разрешения открыт, повторите «Обновить». */
    data object PermissionRequired : InstallOutcome

    data class Failed(
        val error: UpdateError,
    ) : InstallOutcome
}

/**
 * Платформенная часть обновления: как поставить скачанный и проверенный файл.
 * Реализации — по платформам (`composeApp/.../update`), регистрируются в Koin в `PlatformModule`.
 */
interface AppUpdateInstaller {
    /** По этой платформе выбирается ассет релиза. */
    val platform: UpdatePlatform
    val capability: UpdateCapability

    /** Каталог во внутреннем кэше приложения для скачанного файла. */
    val workDirectory: Path

    /**
     * Ставит [file] (уже проверенный по SHA-256). Вызывается только при [UpdateCapability.InAppInstall].
     * Не бросает: любой сбой возвращается как [InstallOutcome.Failed].
     */
    suspend fun install(
        file: Path,
        release: AppRelease,
    ): InstallOutcome
}

/**
 * Установщик для платформ без установки из приложения: только страница релиза. Используется на iOS
 * и как безопасная замена, пока платформенная реализация не подключена.
 */
class OpenPageOnlyInstaller(
    override val platform: UpdatePlatform,
    override val workDirectory: Path,
) : AppUpdateInstaller {
    override val capability: UpdateCapability = UpdateCapability.OpenReleasePage

    override suspend fun install(
        file: Path,
        release: AppRelease,
    ): InstallOutcome = InstallOutcome.Failed(UpdateError.InstallRejected)
}
