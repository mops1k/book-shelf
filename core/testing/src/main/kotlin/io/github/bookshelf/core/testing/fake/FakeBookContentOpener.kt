package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.io.SeekableFile
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.files.DocumentFacts
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Фейк доступа к содержимому книги для тестов.
 *
 * Хранит содержимое по адресу документа, поэтому проверяется настоящий сценарий оптимизации:
 * прочитать → заменить → прочитать снова. Отдельно управляется сбой записи: тогда видно, что
 * вызывающий вернул исходные байты на место, а не оставил книгу битой.
 */
class FakeBookContentOpener : BookContentOpener {

    /** Содержимое по адресу документа. */
    val files = mutableMapOf<String, ByteArray>()

    /** Сколько раз содержимое заменяли. */
    var replacements: Int = 0
        private set

    /** Адреса, по которым запись не удалась. */
    var failingUris: Set<String> = emptySet()

    /**
     * Имена потоков, на которых читали или заменяли содержимое.
     *
     * Нужны тестам про отзывчивость: тяжёлая работа обязана идти не на потоке вызывающего, иначе
     * интерфейс встаёт — именно так на устройстве 30.09.2026 случился ANR карточки книги.
     */
    val threads: MutableList<String> = mutableListOf()

    override fun openStream(documentUri: String): InputStream? {
        threads += Thread.currentThread().name
        return files[documentUri]?.let { bytes -> ByteArrayInputStream(bytes) }
    }

    override fun openSeekableChannel(documentUri: String): SeekableFile? = null

    override fun sizeOf(documentUri: String): Long? = files[documentUri]?.size?.toLong()

    /**
     * Факты документа: размер берётся из содержимого, время — из [lastModified].
     *
     * Отдельно управляемое время нужно тестам про «скан не перечитывает книгу, которую только что
     * переписали»: там важно, что записанное время совпадает с тем, что сообщит провайдер.
     */
    override fun factsOf(documentUri: String): DocumentFacts? =
        files[documentUri]?.let { bytes -> DocumentFacts(bytes.size.toLong(), lastModified) }

    /** Время изменения, которое «сообщает провайдер». */
    var lastModified: Long = 0L

    override fun tempCopy(documentUri: String): AppResult<String> =
        AppResult.failure(AppError.Unknown(message = "временная копия в тесте не нужна"))

    override fun exportCopy(documentUri: String, extension: String): AppResult<String> =
        AppResult.failure(AppError.Unknown(message = "экспорт в тесте не нужен"))

    override fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit> {
        replacements += 1
        threads += Thread.currentThread().name
        if (documentUri in failingUris) {
            return AppResult.failure(AppError.Io(message = "провайдер отказал в записи"))
        }
        files[documentUri] = bytes
        return AppResult.success(Unit)
    }
}
