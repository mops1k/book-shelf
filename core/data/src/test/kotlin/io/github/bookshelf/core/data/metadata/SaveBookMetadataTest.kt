package io.github.bookshelf.core.data.metadata

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.metadata.writer.MetadataEdit
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Сохранение правки метаданных: в приложении и в самом файле книги.
 *
 * Главное, что здесь проверяется, — порядок и честность результата. Правка сначала попадает в базу
 * (она уже работает в приложении), и только потом в файл; отказ записи файла не отменяет правку, но
 * и не выдаётся за успех: причина возвращается наружу, чтобы её увидел человек.
 */
class SaveBookMetadataTest {

    private val repository = FakeBookRepository()
    private val editor = FakeMetadataEditor()

    @Test
    fun `edit is saved in the app and written to the file`() = runBlocking<Unit> {
        repository.bookById = AppResult.success(book())
        val save = SaveBookMetadata(repository, editor)

        val result = save.save(
            bookId = BOOK_ID,
            patch = MetadataPatch(seriesName = "Серия", authorsText = "Иванов, Петров"),
            writeToFile = true,
        )

        assertThat(result.getOrNull()).isEqualTo(MetadataSaveResult(fileWritten = true))
        assertThat(repository.metadataPatches.single().seriesName).isEqualTo("Серия")
        // Авторы разбираются общим разбором: «Иванов, Петров» — это два автора, а не один.
        assertThat(editor.edits.single().authors).containsExactly("Иванов", "Петров").inOrder()
        assertThat(editor.edits.single().seriesName).isEqualTo("Серия")
    }

    @Test
    fun `file failure keeps the in-app edit and reports the reason`() = runBlocking<Unit> {
        repository.bookById = AppResult.success(book())
        editor.result = AppResult.failure(AppError.Io(message = "Нет права записи в папку"))
        val save = SaveBookMetadata(repository, editor)

        val result = save.save(BOOK_ID, MetadataPatch(seriesName = "Серия"), writeToFile = true)

        val saved = result.getOrNull()
        assertThat(saved?.fileWritten).isFalse()
        // Причина — типизированный код: строку выбирает экран («TDD» §8).
        assertThat(saved?.fileFailure).isEqualTo(
            MetadataWriteFailure.WriteFailed(AppError.Io(message = "Нет права записи в папку")),
        )
        // Правка в приложении осталась: отказ записи файла не отменяет работу пользователя.
        assertThat(repository.metadataPatches).hasSize(1)
    }

    @Test
    fun `file is not touched when the user did not ask`() = runBlocking<Unit> {
        repository.bookById = AppResult.success(book())
        val save = SaveBookMetadata(repository, editor)

        val result = save.save(BOOK_ID, MetadataPatch(seriesName = "Серия"), writeToFile = false)

        assertThat(result.getOrNull()).isEqualTo(MetadataSaveResult(fileWritten = false))
        assertThat(editor.edits).isEmpty()
    }

    @Test
    fun `refused save does not touch the file`() = runBlocking<Unit> {
        repository.updateMetadataResult = AppResult.failure(AppError.NotFound(what = "book", message = "нет"))
        val save = SaveBookMetadata(repository, editor)

        val result = save.save(BOOK_ID, MetadataPatch(seriesName = "Серия"), writeToFile = true)

        assertThat(result.errorOrNull()?.message).isEqualTo("нет")
        assertThat(editor.edits).isEmpty()
    }

    private fun book() = Book(
        id = BOOK_ID,
        title = "Книга",
        authorsText = "Автор",
        extension = "epub",
        documentUri = "content://test/book.epub",
        sizeBytes = 10L,
        addedAt = 0L,
    )

    /** Своя реализация порта — фейк, как требует ADR-012: свои интерфейсы не мокаются. */
    private class FakeMetadataEditor : BookMetadataEditor {

        var result: AppResult<Unit> = AppResult.success(Unit)
        val edits = mutableListOf<MetadataEdit>()

        override suspend fun writeToFile(book: Book, edit: MetadataEdit): AppResult<Unit> {
            edits += edit
            return result
        }
    }

    private companion object {
        const val BOOK_ID = "book-1"
    }
}
