package io.github.bookshelf.core.data.metadata

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.registry.ParserRegistry
import io.github.bookshelf.core.metadata.writer.BookFileMetadataWriter
import io.github.bookshelf.core.metadata.writer.MetadataEdit
import io.github.bookshelf.core.metadata.writer.MetadataWriteResult
import io.github.bookshelf.core.storage.files.BookContentOpener
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись правки метаданных в сам файл книги.
 *
 * Зачем отдельный порт: правка чужого файла на устройстве — необратимое действие, и оно должно быть
 * проверяемым. Порядок шагов жёсткий: прочитать файл → собрать новые байты → **проверить их своим же
 * парсером** → и только потом открывать документ на запись. Нечитаемый результат не записывается:
 * испорченная книга хуже незаписанной правки.
 */
interface BookMetadataEditor {

    /**
     * Перезаписать файл книги с правкой.
     *
     * Ошибка несёт причину, по которой файл остался прежним: формат не поддержан, файл не читается,
     * нет права записи, результат не разбирается.
     */
    suspend fun writeToFile(book: Book, edit: MetadataEdit): AppResult<Unit>
}

/** Реализация через SAF: чтение — [BookContentOpener], запись — `ContentResolver`. */
@Singleton
class DefaultBookMetadataEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opener: BookContentOpener,
    private val parsers: ParserRegistry,
) : BookMetadataEditor {

    /** Писатели форматов — без состояния, поэтому создаются здесь, а не тянутся через DI. */
    private val writer = BookFileMetadataWriter()

    override suspend fun writeToFile(book: Book, edit: MetadataEdit): AppResult<Unit> {
        if (edit.isEmpty) return AppResult.success(Unit)
        val original = opener.openStream(book.documentUri)?.use { stream -> stream.readBytes() }
            ?: return AppResult.failure(
                AppError.NotFound(what = "file", message = "Файл книги не открылся для чтения"),
            )
        val rewritten = when (val result = writer.rewrite(book.extension, original, edit)) {
            is MetadataWriteResult.NotSupported -> return AppResult.failure(
                AppError.Unsupported(what = book.extension, message = result.reason),
            )

            is MetadataWriteResult.Rewritten -> result.bytes
        }
        verify(book, rewritten, edit)?.let { reason ->
            return AppResult.failure(AppError.Io(message = reason))
        }
        // Режим `wt` — «писать с начала»: иначе остаток прежнего файла остался бы в хвосте и книга
        // перестала бы разбираться.
        return appRunCatching {
            val output = context.contentResolver.openOutputStream(Uri.parse(book.documentUri), WRITE_TRUNCATE)
                ?: error("Провайдер не открыл файл на запись")
            output.use { stream ->
                stream.write(rewritten)
                stream.flush()
            }
        }
    }

    /**
     * Проверка результата до записи.
     *
     * @return причина, по которой писать нельзя, или `null`, если всё в порядке. Формат без парсера
     *   сюда не доходит: писать в него мы и не умеем ([MetadataWriteResult.NotSupported]).
     */
    private suspend fun verify(book: Book, bytes: ByteArray, edit: MetadataEdit): String? {
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, MAGIC_PREFIX_SIZE))
        val parser = parsers.select(book.extension, null, prefix) ?: return null
        val input = BookInput(
            fileName = book.relativePath.substringAfterLast('/'),
            extension = book.extension,
            mimeType = null,
            sizeBytes = bytes.size.toLong(),
            openStream = { ByteArrayInputStream(bytes) },
            openSeekable = { null },
            bytes = { offset, length ->
                bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
            },
        )
        val metadata = when (val result = parser.parse(input)) {
            is ParseResult.Success -> result.metadata
            is ParseResult.Partial -> result.metadata
            else -> return "Правка не записана: файл после неё не читается"
        }
        // Проверяются именно те поля, которых коснулась правка: «файл ещё читается» — слишком
        // слабая гарантия, правка могла не попасть в него вовсе.
        val wantedTitle = edit.title?.trim()
        val wantedSeries = edit.seriesName?.trim()
        val wantedAuthors = edit.authors?.map(String::trim)
        val wantedDescription = edit.description?.trim()
        val missing = when {
            metadata.title.isBlank() -> "название"
            wantedTitle != null && metadata.title.trim() != wantedTitle -> "название"
            // `orEmpty()`: пустая строка в правке означает «убрать значение», и в файле его
            // действительно не остаётся — сравнивать с `null` напрямую значило бы отвергать
            // правильную очистку (найдено на устройстве: серия не убиралась из файла).
            wantedSeries != null && metadata.seriesName?.trim().orEmpty() != wantedSeries -> "серия"
            edit.seriesIndex != null && metadata.seriesIndex != edit.seriesIndex -> "номер в серии"
            wantedAuthors != null && metadata.authors.map(String::trim) != wantedAuthors -> "авторы"
            wantedDescription != null && metadata.description?.trim().orEmpty() != wantedDescription ->
                "описание"
            else -> null
        }
        return missing?.let { field -> "Правка не записана: файл после неё не отдаёт «$field»" }
    }

    private companion object {
        const val WRITE_TRUNCATE = "wt"
        const val MAGIC_PREFIX_SIZE = 256
    }
}
