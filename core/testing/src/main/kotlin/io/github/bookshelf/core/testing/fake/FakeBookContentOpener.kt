package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.storage.files.BookContentOpener
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.channels.FileChannel

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

    override fun openStream(documentUri: String): InputStream? =
        files[documentUri]?.let { bytes -> ByteArrayInputStream(bytes) }

    override fun openSeekableChannel(documentUri: String): FileChannel? = null

    override fun sizeOf(documentUri: String): Long? = files[documentUri]?.size?.toLong()

    override fun tempCopy(documentUri: String): AppResult<String> =
        AppResult.failure(AppError.Unknown(message = "временная копия в тесте не нужна"))

    override fun exportCopy(documentUri: String): AppResult<String> =
        AppResult.failure(AppError.Unknown(message = "экспорт в тесте не нужен"))

    override fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit> {
        replacements += 1
        if (documentUri in failingUris) {
            return AppResult.failure(AppError.Io(message = "провайдер отказал в записи"))
        }
        files[documentUri] = bytes
        return AppResult.success(Unit)
    }
}
