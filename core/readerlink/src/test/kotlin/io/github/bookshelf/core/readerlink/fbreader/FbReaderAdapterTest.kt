package io.github.bookshelf.core.readerlink.fbreader

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Прогресс книги по данным читалки (`FbReaderAdapter`).
 *
 * Проверяется то, что читалка отдаёт на самом деле (спайк 29.09.2026): позиция **текущей открытой**
 * книги (абзац из 701) и общее число абзацев (601). Процент считается из них, а книга сопоставляется
 * по адресу, которым мы её отдали читалке.
 *
 * Почему это не «арифметика ради арифметики»: процент попадает на полку, и ошибка здесь выглядит
 * как «книга прочитана на 0 %», хотя человек читает её неделю, либо как «прочитано 100 %» после
 * первой страницы. Поэтому отдельно проверяются границы (0 %, больше 100 %), отсутствие книги и
 * неизвестное число абзацев — последнее нельзя показывать как 0 %.
 */
class FbReaderAdapterTest {

    @Test
    fun `progress is the share of paragraphs already read`() = runBlocking {
        val source = adapter(
            book = openBook(path = BOOK_PATH),
            position = position(paragraph = 503, paragraphs = 2012),
        )

        val snapshot = source.progressFor(BOOK_PATH).getOrNull()

        // 503 из 2012 — ровно четверть книги.
        assertThat(snapshot?.percent).isWithin(0.01f).of(25f)
        assertThat(snapshot?.pageIndex).isEqualTo(503)
    }

    @Test
    fun `book opened at the first paragraph is zero percent`() = runBlocking {
        val source = adapter(
            book = openBook(path = BOOK_PATH),
            position = position(paragraph = 0, paragraphs = 2012),
        )

        assertThat(source.progressFor(BOOK_PATH).getOrNull()?.percent).isWithin(0.01f).of(0f)
    }

    @Test
    fun `position past the end is capped at hundred percent`() = runBlocking {
        // Читалка может отдать позицию за концом книги, если текст переразметили после сохранения
        // позиции: показывать «104 %» нельзя.
        val source = adapter(
            book = openBook(path = BOOK_PATH),
            position = position(paragraph = 2100, paragraphs = 2012),
        )

        assertThat(source.progressFor(BOOK_PATH).getOrNull()?.percent).isWithin(0.01f).of(100f)
    }

    @Test
    fun `another book in the reader is not our progress`() = runBlocking {
        val source = adapter(
            book = openBook(path = "content://tree/other"),
            position = position(paragraph = 100, paragraphs = 200),
        )

        val result = source.progressFor(BOOK_PATH)

        // Открыта чужая книга — это не прогресс нашей: запись не должна получить чужой процент.
        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `no book open is not our progress`() = runBlocking {
        val source = adapter(book = null, position = position(paragraph = 10, paragraphs = 100))

        assertThat(source.progressFor(BOOK_PATH).isFailure).isTrue()
    }

    @Test
    fun `unknown paragraph count gives no progress at all`() = runBlocking {
        // Ноль абзацев означает «читалка ещё не посчитала». Вернуть тут 0 % — значит соврать:
        // книга не «не начата», о ней просто нет данных.
        val source = adapter(
            book = openBook(path = BOOK_PATH),
            position = position(paragraph = 5, paragraphs = 0),
        )

        assertThat(source.progressFor(BOOK_PATH).isFailure).isTrue()
    }

    @Test
    fun `missing position gives no progress`() = runBlocking {
        val source = adapter(book = openBook(path = BOOK_PATH), position = null)

        assertThat(source.progressFor(BOOK_PATH).isFailure).isTrue()
    }

    private fun adapter(book: FbReaderOpenBook?, position: FbReaderReadingPosition?) =
        FbReaderAdapter(FakeFbReaderApi(book = book, position = position))

    private fun openBook(path: String) = FbReaderOpenBook(path = path, title = "Книга", language = "ru")

    private fun position(paragraph: Int, paragraphs: Int) = FbReaderReadingPosition(
        paragraphIndex = paragraph,
        elementIndex = 0,
        paragraphsNumber = paragraphs,
    )

    /** Свой фейк: `FbReaderApi` — интерфейс проекта, `mockk` по своим типам запрещён (ADR-012). */
    private class FakeFbReaderApi(
        private val book: FbReaderOpenBook?,
        private val position: FbReaderReadingPosition?,
    ) : FbReaderApi {

        private var connected = true

        override suspend fun connect(): Boolean = connected

        override suspend fun disconnect() {
            connected = false
        }

        override fun isAvailable(): Boolean = connected

        override suspend fun currentBook(): FbReaderOpenBook? = book

        override suspend fun currentPosition(): FbReaderReadingPosition? = position
    }

    private companion object {
        const val BOOK_PATH =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fbook.fb2"
    }
}
