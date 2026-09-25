package com.aniko.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Релиз в ответе `GET /repos/{owner}/{repo}/releases` (только нужные поля; лишние игнорируются). */
@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/**
 * Приводит DTO к [AppRelease]. `null`, если это черновик или тег не является версией SemVer
 * (`v0.1.0`). Ассеты с адресом вне [isTrustedUpdateUrl] отбрасываются; страница релиза с
 * недоверенным адресом заменяется на [fallbackPageUrl].
 */
internal fun GitHubReleaseDto.toAppRelease(fallbackPageUrl: String): AppRelease? {
    val version = AppVersion.parse(tagName)?.takeUnless { draft } ?: return null
    return AppRelease(
        version = version,
        tag = tagName,
        title = name?.takeIf { it.isNotBlank() } ?: tagName,
        notes = body.orEmpty(),
        pageUrl = htmlUrl?.takeIf(::isTrustedUpdateUrl) ?: fallbackPageUrl,
        publishedAt = publishedAt,
        assets =
            assets
                .filter { isTrustedUpdateUrl(it.browserDownloadUrl) }
                .map { ReleaseAsset(name = it.name, sizeBytes = it.size, downloadUrl = it.browserDownloadUrl) },
    )
}
