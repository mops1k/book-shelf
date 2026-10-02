package io.github.bookshelf.core.storage.saf

import io.github.bookshelf.core.common.error.AppResult

/**
 * Читатель дерева, который сам выбирает способ доступа по адресу источника.
 *
 * Адрес SAF (`content://…`) обслуживает системный провайдер. Адрес-путь файловой системы (`/…`)
 * читается напрямую: так работает съёмный носитель, который прошивка смонтировала только приватно
 * (`/mnt/media_rw/<uuid>`, проверено на Bigme B6 Color 02.10.2026) — системный провайдер про такой
 * том не знает, а приложение с доступом ко всем файлам читает его сам.
 *
 * Выбор здесь, а не в обходчике: обходчик работает с деревом вообще и не должен знать, чем оно
 * обслуживается; ему нужен один контракт [SafTreeReader].
 */
class DispatchingTreeReader(
    private val saf: SafTreeReader,
    private val direct: SafTreeReader,
) : SafTreeReader {

    override suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>> =
        readerFor(treeUri).children(treeUri, parentDocumentId)

    override suspend fun takePersistablePermission(treeUri: String, modeFlags: Int): AppResult<Unit> =
        readerFor(treeUri).takePersistablePermission(treeUri, modeFlags)

    override fun hasPersistedPermission(treeUri: String): Boolean =
        readerFor(treeUri).hasPersistedPermission(treeUri)

    override fun hasWritePermission(treeUri: String): Boolean =
        readerFor(treeUri).hasWritePermission(treeUri)

    override fun rootDocumentId(treeUri: String): String = readerFor(treeUri).rootDocumentId(treeUri)

    override fun documentUri(treeUri: String, documentId: String): String =
        readerFor(treeUri).documentUri(treeUri, documentId)

    override fun relativePath(treeUri: String, documentId: String): String =
        readerFor(treeUri).relativePath(treeUri, documentId)

    /** Адрес с `content://` — провайдер, любой другой непустой — путь файловой системы. */
    private fun readerFor(treeUri: String): SafTreeReader =
        if (treeUri.startsWith(CONTENT_SCHEME, ignoreCase = true)) saf else direct

    private companion object {
        const val CONTENT_SCHEME = "content://"
    }
}
