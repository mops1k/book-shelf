package io.github.bookshelf.core.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Копии файла, ход чтения, пересканирование и дубликаты.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookRepositoryCopiesTest : BookRepositoryTestBase() {
    @Test
    fun `book by id carries the folder and path for the card`() = runBlocking {
        givenLibrary()

        val book = repository.observeById("war").first()

        // Карточка показывает «Books / Война и мир.fb2»: путь и папка должны доходить до домена,
        // иначе строки «Путь» и «Документ» остаются пустыми (найдено 28.09.2026).
        assertThat(book?.relativePath).isEqualTo("Война и мир.fb2")
        assertThat(book?.sourceId).isEqualTo(SOURCE_ID)
    }

    @Test
    fun stored_share_of_reading_progress_is_exposed_as_percent() = runBlocking {
        database.bookDao().insert(book(id = "progress", title = "Книга", author = "", ext = "fb2"))
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = "progress",
                percent = 0.25f,
                updatedAt = 1_000L,
                source = "FBREADER_AIDL",
            ),
        )

        val loaded = repository.getById("progress").getOrNull()

        // В таблице хранится доля 0..1, а полка печатает значение со знаком процента: отдай мы
        // наружу долю — книга, прочитанная на четверть, показывалась бы как «читаю 0 %».
        assertThat(loaded?.progressPercent).isWithin(0.01f).of(25f)
    }

    @Test
    fun deletePermanently_removes_the_file_and_the_row() = runBlocking {
        database.bookDao().insert(book(id = "gone", title = "Книга", author = "", ext = "fb2"))
        files.refuse = null

        val result = repository.deletePermanently(listOf("gone")).getOrNull()

        assertThat(result?.deleted).isEqualTo(1)
        assertThat(result?.failed).isEqualTo(0)
        assertThat(database.bookDao().getById("gone")).isNull()
    }

    @Test
    fun deletePermanently_keeps_the_row_when_the_provider_refuses() = runBlocking {
        database.bookDao().insert(book(id = "kept", title = "Книга", author = "", ext = "fb2"))
        files.refuse = io.github.bookshelf.core.common.error.AppError.Unknown(message = "нет доступа к папке")

        val result = repository.deletePermanently(listOf("kept")).getOrNull()

        // Строку оставляем: исчезни она, следующий скан вернул бы книгу на полку, и удаление
        // выглядело бы отменившимся само.
        assertThat(result?.deleted).isEqualTo(0)
        assertThat(result?.failed).isEqualTo(1)
        assertThat(database.bookDao().getById("kept")).isNotNull()
    }

    @Test
    fun findDuplicates_reads_the_whole_library() = runBlocking {
        database.bookDao().insert(book(id = "one", title = "Книга", author = "", ext = "fb2"))
        database.bookDao().insert(book(id = "two", title = "Книга", author = "", ext = "fb2"))
        hashes.hash = "одинаковый"

        val report = repository.findDuplicates().getOrNull()

        // Обе книги одного размера и с одинаковым содержимым: одна группа, одна копия к удалению.
        assertThat(report?.groups).hasSize(1)
        assertThat(report?.duplicatesToRemove).isEqualTo(1)
    }
}
