package io.github.bookshelf.core.update.source

import io.github.bookshelf.core.update.model.AppVersion
import io.github.bookshelf.core.update.model.ReleaseAsset
import io.github.bookshelf.core.update.model.ReleaseInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Разбор ответа `GET /repos/{owner}/{repo}/releases/latest` (ADR-018).
 *
 * GitHub добавляет поля без предупреждения, поэтому JSON читается с `ignoreUnknownKeys`.
 * Любая неожиданность (битый JSON, нечитаемый тег, черновик) даёт `null`: показать «обновление
 * есть» по непонятному ответу хуже, чем не показать ничего.
 */
object GitHubReleaseJson {

    private val json = Json { ignoreUnknownKeys = true }

    /** Разобрать ответ; `null` — ответ не является пригодным релизом. */
    fun parse(raw: String): ReleaseInfo? {
        if (raw.isBlank()) return null
        val dto = runCatching { json.decodeFromString<ReleaseDto>(raw) }.getOrNull() ?: return null
        if (dto.draft || dto.prerelease) return null
        val version = AppVersion.parse(dto.tagName) ?: return null
        return ReleaseInfo(
            version = version,
            versionName = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
            notes = dto.body.orEmpty(),
            pageUrl = dto.htmlUrl.orEmpty(),
            assets = dto.assets.orEmpty().mapNotNull { asset ->
                val name = asset.name ?: return@mapNotNull null
                val url = asset.downloadUrl ?: return@mapNotNull null
                ReleaseAsset(name = name, downloadUrl = url)
            },
        )
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto>? = null,
    )

    @Serializable
    private data class AssetDto(
        val name: String? = null,
        @SerialName("browser_download_url") val downloadUrl: String? = null,
    )
}
