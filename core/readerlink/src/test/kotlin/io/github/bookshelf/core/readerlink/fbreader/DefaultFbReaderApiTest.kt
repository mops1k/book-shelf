package io.github.bookshelf.core.readerlink.fbreader

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Разбор ответов сервиса FBReader по кодам (`DefaultFbReaderApi`).
 *
 * Проверяется то, что нельзя увидеть на устройстве вручную: какой код за что отвечает и что
 * делается с неожиданным ответом. Ошибка здесь тихая — например, число абзацев, прочитанное из
 * кода позиции, даёт правдоподобный, но неверный процент на полке.
 *
 * Отдельно проверяются «пустые» ответы: читалка отвечает всегда, поэтому «книга не открыта»,
 * «значение не того типа» и «сервис недоступен» надо различать и не превращать в данные.
 */
class DefaultFbReaderApiTest {

    @Test
    fun `open book is assembled from path, title and language`() = runBlocking {
        val api = api(
            mapOf(
                FbReaderCodes.GET_BOOK_FILE_PATH to FbReaderValue.Text(BOOK_PATH),
                FbReaderCodes.GET_BOOK_TITLE to FbReaderValue.Text("X-COM: Первый контакт"),
                FbReaderCodes.GET_BOOK_LANGUAGE to FbReaderValue.Text("ru"),
            ),
        )

        val book = api.currentBook()

        assertThat(book?.path).isEqualTo(BOOK_PATH)
        assertThat(book?.title).isEqualTo("X-COM: Первый контакт")
        assertThat(book?.language).isEqualTo("ru")
    }

    @Test
    fun `no path means no book is open`() = runBlocking {
        // Сервис отвечает и без открытой книги: путь приходит `null`.
        val api = api(mapOf(FbReaderCodes.GET_BOOK_FILE_PATH to FbReaderValue.Text(null)))

        assertThat(api.currentBook()).isNull()
    }

    @Test
    fun `blank path means no book is open`() = runBlocking {
        val api = api(mapOf(FbReaderCodes.GET_BOOK_FILE_PATH to FbReaderValue.Text("   ")))

        assertThat(api.currentBook()).isNull()
    }

    @Test
    fun `unexpected value type is not a book`() = runBlocking {
        // Если формат ответа изменится, выдумывать книгу нельзя.
        val api = api(mapOf(FbReaderCodes.GET_BOOK_FILE_PATH to FbReaderValue.Number(5)))

        assertThat(api.currentBook()).isNull()
    }

    @Test
    fun `missing response means no book`() = runBlocking {
        val api = api(emptyMap())

        assertThat(api.currentBook()).isNull()
    }

    @Test
    fun `position is code 701 together with the paragraph count`() = runBlocking {
        val api = api(
            mapOf(
                FbReaderCodes.GET_PAGE_START to FbReaderValue.Position(paragraphIndex = 4, elementIndex = 1),
                FbReaderCodes.GET_PARAGRAPHS_NUMBER to FbReaderValue.Number(2012),
            ),
        )

        val position = api.currentPosition()

        assertThat(position?.paragraphIndex).isEqualTo(4)
        assertThat(position?.elementIndex).isEqualTo(1)
        assertThat(position?.paragraphsNumber).isEqualTo(2012)
    }

    @Test
    fun `position without paragraphs is reported as unknown`() = runBlocking {
        // Числа абзацев нет — отдаём 0, а решение «показывать нечего» принимает адаптер.
        val api = api(
            mapOf(FbReaderCodes.GET_PAGE_START to FbReaderValue.Position(paragraphIndex = 4, elementIndex = 0)),
        )

        assertThat(api.currentPosition()?.paragraphsNumber).isEqualTo(0)
    }

    @Test
    fun `negative paragraph count is treated as unknown`() = runBlocking {
        val api = api(
            mapOf(
                FbReaderCodes.GET_PAGE_START to FbReaderValue.Position(paragraphIndex = 4, elementIndex = 0),
                FbReaderCodes.GET_PARAGRAPHS_NUMBER to FbReaderValue.Number(-1),
            ),
        )

        assertThat(api.currentPosition()?.paragraphsNumber).isEqualTo(0)
    }

    @Test
    fun `position of unexpected type gives no position`() = runBlocking {
        val api = api(
            mapOf(
                FbReaderCodes.GET_PAGE_START to FbReaderValue.Text("не позиция"),
                FbReaderCodes.GET_PARAGRAPHS_NUMBER to FbReaderValue.Number(2012),
            ),
        )

        assertThat(api.currentPosition()).isNull()
    }

    @Test
    fun `session availability is passed through`() = runBlocking {
        val unavailable = api(emptyMap(), available = false)
        assertThat(unavailable.connect()).isFalse()
        assertThat(unavailable.isAvailable()).isFalse()

        val available = api(emptyMap(), available = true)
        assertThat(available.connect()).isTrue()
        assertThat(available.isAvailable()).isTrue()

        available.disconnect()
        assertThat(available.isAvailable()).isFalse()
    }

    private fun api(
        responses: Map<Int, FbReaderValue>,
        available: Boolean = true,
    ) = DefaultFbReaderApi(FakeTransport(responses = responses, available = available))

    /** Свой фейк: `FbReaderTransport` — интерфейс проекта, `mockk` по своим типам запрещён. */
    private class FakeTransport(
        private val responses: Map<Int, FbReaderValue>,
        private val available: Boolean,
    ) : FbReaderTransport {

        private var connected = false

        override suspend fun connect(): Boolean {
            connected = available
            return available
        }

        override fun isConnected(): Boolean = connected

        override suspend fun disconnect() {
            connected = false
        }

        override suspend fun request(code: Int): FbReaderValue? = responses[code]
    }

    private companion object {
        const val BOOK_PATH =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fbook.fb2"
    }
}
