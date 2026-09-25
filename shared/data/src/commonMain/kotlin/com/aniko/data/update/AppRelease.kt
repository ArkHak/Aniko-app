package com.aniko.data.update

/**
 * Платформа, для которой подбирается ассет релиза. Имена ассетов — часть релизного процесса:
 * `aniko-vX.Y.Z-android.apk`, `aniko-vX.Y.Z-macos.dmg`, `aniko-vX.Y.Z-ios-unsigned.ipa` (см.
 * `docs/DEVELOPMENT.md`, раздел «Релизы»).
 */
enum class UpdatePlatform(
    val assetSuffix: String?,
) {
    Android("-android.apk"),
    MacOs("-macos.dmg"),
    Ios("-ios-unsigned.ipa"),

    /** Windows/Linux и прочее: готовых сборок нет, обновление только через страницу релиза. */
    Other(null),
}

/** Файл, приложенный к релизу GitHub. */
data class ReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

/**
 * Релиз Aniko на GitHub.
 *
 * @param notes текст release notes как есть (Markdown); для показа в диалоге обрезается и
 * очищается [ReleaseNotesFormatter].
 * @param pageUrl страница релиза — запасной путь обновления, когда установка из приложения невозможна.
 */
data class AppRelease(
    val version: AppVersion,
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String?,
    val assets: List<ReleaseAsset>,
) {
    /** Ассет установщика под [platform]; `null` — в релизе нет файла под эту платформу. */
    fun assetFor(platform: UpdatePlatform): ReleaseAsset? {
        val suffix = platform.assetSuffix ?: return null
        return assets.firstOrNull { it.name.startsWith(ASSET_PREFIX) && it.name.endsWith(suffix) }
    }

    /** `SHA256SUMS.txt` этого релиза (формат `sha256sum`); `null` — релиз выложен без контрольных сумм. */
    val checksumsAsset: ReleaseAsset?
        get() = assets.firstOrNull { it.name == CHECKSUMS_ASSET_NAME }

    companion object {
        const val ASSET_PREFIX = "aniko-"
        const val CHECKSUMS_ASSET_NAME = "SHA256SUMS.txt"
    }
}
