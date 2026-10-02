package io.github.bookshelf.core.storage.saf

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import java.io.File

/**
 * Обход дерева по обычному пути файловой системы.
 *
 * Нужен там, где SAF бессилен: например, прошивка монтирует съёмный носитель только приватно
 * (`/mnt/media_rw/<uuid>`, проверено на Bigme B6 Color 02.10.2026), и системный провайдер про такой
 * том не знает. Приложение с доступом ко всем файлам читает том напрямую, а этот читатель даёт
 * обходчику тот же контракт, что и SAF ([SafTreeReader]).
 *
 * Идентификатор документа — **путь относительно корня источника** (`Books/Лем/Солярис.fb2`), а не
 * абсолютный путь: корень носителя при переподключении может оказаться в другой точке монтирования,
 * а относительный путь остаётся прежним, и книга не превращается в новую.
 *
 * @param rootOfPath корень дерева для адреса источника; по умолчанию адрес источника и есть корень.
 */
class DirectPathTreeReader(
    private val rootOfPath: (String) -> File = { source -> File(source) },
) : SafTreeReader {

    override suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>> {
        val root = rootOfPath(treeUri)
        val directory = resolve(root, parentDocumentId)
            ?: return AppResult.failure(
                AppError.InvalidArgument(what = "parentDocumentId", message = "Путь выходит за корень источника"),
            )
        if (!directory.isDirectory) {
            return AppResult.failure(AppError.NotFound(what = "folder", message = directory.absolutePath))
        }
        val entries = directory.listFiles()
            ?: return AppResult.failure(
                AppError.PermissionDenied(message = "Папка недоступна: ${directory.absolutePath}"),
            )
        return AppResult.success(
            entries
                .filter { entry -> entry.name.isNotEmpty() }
                .sortedBy { entry -> entry.name }
                .map { entry -> entry.toDocumentEntry(root) },
        )
    }

    /**
     * Разрешение сохранять нечего: доступ к пути даёт системное разрешение приложения, а не провайдер.
     *
     * Метод всё равно проверяет, что корень читается: иначе источник считался бы «доступным»,
     * и сканирование падало бы позже, менее понятным способом.
     */
    override suspend fun takePersistablePermission(treeUri: String, modeFlags: Int): AppResult<Unit> {
        val root = rootOfPath(treeUri)
        return if (root.isDirectory && root.canRead()) {
            AppResult.success(Unit)
        } else {
            AppResult.failure(AppError.PermissionDenied(message = "Нет доступа к папке: ${root.absolutePath}"))
        }
    }

    override fun hasPersistedPermission(treeUri: String): Boolean =
        rootOfPath(treeUri).let { root -> root.isDirectory && root.canRead() }

    override fun hasWritePermission(treeUri: String): Boolean =
        rootOfPath(treeUri).let { root -> root.isDirectory && root.canWrite() }

    /** Корень дерева — сам путь: относительный идентификатор корня пуст. */
    override fun rootDocumentId(treeUri: String): String = ""

    /** Адрес документа — обычный путь файловой системы: по нему книга читается напрямую. */
    override fun documentUri(treeUri: String, documentId: String): String =
        resolve(rootOfPath(treeUri), documentId)?.absolutePath.orEmpty()

    /** Путь относительно корня и есть идентификатор: преобразовывать нечего. */
    override fun relativePath(treeUri: String, documentId: String): String = documentId.trim('/')

    private fun File.toDocumentEntry(root: File): DocumentEntry {
        val relative = relativeTo(root).invariantSeparatorsPath
        return DocumentEntry(
            documentId = relative,
            displayName = name,
            isDirectory = isDirectory,
            sizeBytes = length().takeIf { !isDirectory },
            lastModified = lastModified().takeIf { it > 0L },
            // MIME у файловой системы нет: формат определяется по расширению и магии байт.
            mimeType = null,
        )
    }

    /**
     * Путь внутри корня: `documentId` пустой строкой означает сам корень, `..` наружу не пускается.
     *
     * @return файл или `null`, если путь уводит за пределы корня (защита от «`../../etc/passwd`»).
     */
    private fun resolve(root: File, documentId: String): File? {
        val trimmed = documentId.trim().trim('/')
        val candidate = if (trimmed.isEmpty()) root else File(root, trimmed)
        val rootPath = root.absoluteFile.normalize().path
        val candidatePath = candidate.absoluteFile.normalize().path
        val insideRoot = candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
        return candidate.takeIf { insideRoot }
    }
}
