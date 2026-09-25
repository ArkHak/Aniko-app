package com.aniko.data.update

import okio.ByteString.Companion.encodeUtf8
import okio.Path
import okio.Path.Companion.toPath

/** Общие фикстуры тестов обновления. */
internal object UpdateTestSupport {
    const val REPO = "https://github.com/ArkHak/Aniko-app/releases/download"

    fun asset(
        name: String,
        tag: String = "v0.2.0",
        size: Long = 10,
    ) = ReleaseAsset(name = name, sizeBytes = size, downloadUrl = "$REPO/$tag/$name")

    fun release(
        version: String,
        assets: List<ReleaseAsset> = defaultAssets(version),
        notes: String = "## Что нового\n- Улучшения",
    ): AppRelease {
        val parsed = requireNotNull(AppVersion.parse(version))
        return AppRelease(
            version = parsed,
            tag = "v$parsed",
            title = "Aniko v$parsed",
            notes = notes,
            pageUrl = "https://github.com/ArkHak/Aniko-app/releases/tag/v$parsed",
            publishedAt = null,
            assets = assets,
        )
    }

    fun defaultAssets(version: String): List<ReleaseAsset> =
        listOf(
            asset("aniko-v$version-android.apk", "v$version"),
            asset("aniko-v$version-macos.dmg", "v$version"),
            asset("aniko-v$version-ios-unsigned.ipa", "v$version"),
            asset("SHA256SUMS.txt", "v$version"),
        )

    fun sha256Of(text: String): String = text.encodeUtf8().sha256().hex()

    val workDir: Path = "/work/updates".toPath()
}
