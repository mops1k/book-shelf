package io.github.bookshelf.core.data.reader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressFileReader
import io.github.bookshelf.core.storage.files.filePathOf
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Чтение файлов рядом с книгой через SAF или файловую систему.
 *
 * Относительный путь от адаптера читалки превращается здесь в конкретный адрес:
 * * книга из папки SAF (обычный случай) — документ внутри того же дерева: к идентификатору дерева
 *   добавляется относительный путь книги и имя файла-соседа, и `DocumentsProvider` открывает поток;
 * * книга со съёмного носителя читается по обычному пути (прошивки без публичной точки `/storage`)
 *   — файл-сосед ищется рядом с реальным путём.
 *
 * Ничего не создаёт и не меняет: если файла нет или провайдер отказал, возвращается `null` —
 * «прогресса нет» — без исключений наружу. Чтение ограничено по размеру: sidecar — файл в несколько
 * килобайт, но на карте его может подменить кто угодно, и загружать в память гигабайт текста нельзя.
 */
@Singleton
class BookSidecarFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : ProgressFileReader {

    override suspend fun readNearBook(book: ProgressBookRef, sibling: String): String? =
        withContext(dispatchers.io) { runCatching { read(book, sibling) }.getOrNull() }

    private fun read(book: ProgressBookRef, sibling: String): String? {
        val path = filePathOf(book.documentUri)
        val stream = if (path != null) {
            val file = File(File(path).parentFile, sibling)
            if (file.isFile) file.inputStream() else null
        } else {
            context.contentResolver.openInputStream(siblingUri(book, sibling))
        }
        return stream?.use { it.readLimited(LIMIT_BYTES) }
    }

    /**
     * Адрес документа-соседа внутри того же дерева SAF.
     *
     * Идентификатор берётся из адреса книги (он содержит и дерево, и документ), поэтому отдельного
     * доступа к каталогу провайдера не нужно — важно лишь, что дерево у книги и у её sidecar одно.
     */
    private fun siblingUri(book: ProgressBookRef, sibling: String): Uri {
        val document = Uri.parse(book.documentUri)
        val treeId = DocumentsContract.getTreeDocumentId(document)
        val folder = book.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val target = if (folder.isEmpty()) "$treeId/$sibling" else "$treeId/$folder/$sibling"
        return DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(document.authority, treeId),
            target,
        )
    }

    /**
     * Прочитать не больше [limit] байт и вернуть их текстом.
     *
     * Обрезка может разорвать многобайтовый символ в самом конце: для разбора это безопасно — нужные
     * поля берутся из начала файла, а «хвост» текста нам не нужен вовсе.
     */
    private fun InputStream.readLimited(limit: Int): String {
        val buffer = ByteArray(READ_CHUNK)
        val collected = ByteArrayOutputStream()
        var total = 0
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - total))
            if (read <= 0) break
            collected.write(buffer, 0, read)
            total += read
        }
        return collected.toString(Charsets.UTF_8.name())
    }

    private companion object {

        /**
         * Потолок чтения: sidecar KOReader — около четырёх килобайт, двести пятьдесят шесть с запасом
         * покрывают любой формат состояния читалки и при этом не позволяют прочитать большой файл.
         */
        const val LIMIT_BYTES = 256 * 1024

        const val READ_CHUNK = 8 * 1024
    }
}
