package io.github.bookshelf.core.update.model

/**
 * Последний опубликованный релиз (ADR-018).
 *
 * @param version разобранный тег — по нему решается «новее ли».
 * @param notes тело релиза: что нового; показывается в диалоге.
 * @param pageUrl страница релиза — запасной путь, если скачивание не удалось.
 */
data class ReleaseInfo(
    val version: AppVersion,
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
)
