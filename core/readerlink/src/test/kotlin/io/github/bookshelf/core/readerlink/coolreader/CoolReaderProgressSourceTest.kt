package io.github.bookshelf.core.readerlink.coolreader

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressDatabase
import io.github.bookshelf.core.readerlink.progress.ProgressQuery
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Прогресс книги из базы CoolReader (`CoolReaderProgressSource`).
 *
 * Проверяется по живой базе с устройства (Bigme HiBreak, 05.10.2026): `book.pathname` — реальный
 * путь книги, `bookmark` с `type = 0` (`Bookmark.TYPE_LAST_POSITION`) — последняя позиция, а
 * `percent` — это 1/100 процента (в crengine: «position in 1/100 of percents (0..10000)»), то есть
 * 1874 — это 18,74 %.
 *
 * Опознание идёт по имени файла и хвосту относительного пути: в базе CoolReader лежит **реальный
 * путь** (`/sdcard/Books/…`), а у нас адрес SAF, поэтому переводить одно в другое мы не берёмся —
 * вместо догадок сверяем имя файла и, если имён несколько, хвост пути. Если совпадение
 * неоднозначное, лучше не записать прогресс, чем записать его чужой книге.
 */
class CoolReaderProgressSourceTest {

    @Test
    fun `the source names itself as the coolReader channel`() = runBlocking {
        val source = source()

        assertThat(source.id).isEqualTo(ProgressSourceId.COOLREADER_DB)
        // CoolReader не отдаёт наружу «что открыто»: его читают по конкретной книге.
        assertThat(source.knowsOpenBook).isFalse()
        assertThat(source.openedBook()).isNull()
    }

    @Test
    fun `the last position of the book becomes its progress`() = runBlocking {
        val source = connected(
            row(pathname = BOOK_PATH, filename = BOOK_NAME, percent = "1874", startPos = "/body/x", timeStamp = "100"),
        )

        val snapshot = source.progressFor(ourBook()).getOrNull()

        // 1874 в базе — это 18,74 %.
        assertThat(snapshot?.percent).isWithin(0.001f).of(18.74f)
        assertThat(snapshot?.locatorJson).isEqualTo("/body/x")
    }

    @Test
    fun `only the last position is requested from the database`() = runBlocking {
        val database = FakeDatabase(rows = listOf(row(pathname = BOOK_PATH, filename = BOOK_NAME)))
        val source = CoolReaderProgressSource(databases = database)
        source.connect()

        // В `bookmark` лежат и обычные закладки (type = 1), и последняя позиция (type = 0):
        // спрашивать надо только последнюю, иначе прогрессом станет чья-то закладка.
        assertThat(database.queries.single().args).containsExactly("0")
        assertThat(database.queries.single().sql).contains("bookmark")
        source.disconnect()
    }

    @Test
    fun `the freshest position wins when the book has several`() = runBlocking {
        val source = connected(
            row(pathname = BOOK_PATH, filename = BOOK_NAME, percent = "100", timeStamp = "100"),
            row(pathname = BOOK_PATH, filename = BOOK_NAME, percent = "2500", timeStamp = "200"),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(25f)
    }

    @Test
    fun `another book is not our progress`() = runBlocking {
        val source = connected(
            row(pathname = "/sdcard/Books/Другая.epub", filename = "Другая.epub", percent = "5000"),
        )

        val result = source.progressFor(ourBook())

        // Записать чужой процент хуже, чем не записать ничего.
        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `among same-named books the one with the matching path tail wins`() = runBlocking {
        val source = connected(
            row(pathname = "/sdcard/Books/Другая/Книга.fb2.epub", filename = BOOK_NAME, percent = "100"),
            row(pathname = "/sdcard/Books/Лем/Книга.fb2.epub", filename = BOOK_NAME, percent = "4200"),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(42f)
    }

    @Test
    fun `same-named books with unrelated paths are not guessed`() = runBlocking {
        // Обе книги зовутся одинаково и ни одна не совпала по хвосту пути: выбрать «первую» —
        // значит записать прогресс наугад.
        val source = connected(
            row(pathname = "/sdcard/Other/Книга.fb2.epub", filename = BOOK_NAME, percent = "100"),
            row(pathname = "/mnt/media_rw/1/Книга.fb2.epub", filename = BOOK_NAME, percent = "200"),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a database we cannot read gives no progress`() = runBlocking {
        val source = CoolReaderProgressSource(databases = FakeDatabase(rows = null))

        // Читалки может не быть, а на flavor play нет доступа ко всей памяти — это «прогресса нет»,
        // а не «книга не начата».
        assertThat(source.connect()).isFalse()
        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `an empty database gives no progress`() = runBlocking {
        val source = connected()

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `an unreadable percent does not become zero`() = runBlocking {
        val source = connected(
            row(pathname = BOOK_PATH, filename = BOOK_NAME, percent = "", startPos = "/body/x"),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a root-level book is not confused with a same-named file elsewhere`() = runBlocking {
        // Книга лежит в корне папки библиотеки: относительный путь — одно имя файла, поэтому одного
        // хвоста мало и нужна ещё папка библиотеки из адреса документа.
        val source = connected(
            row(pathname = "/sdcard/Other/$ROOT_BOOK_NAME", filename = ROOT_BOOK_NAME, percent = "5000"),
        )

        assertThat(source.progressFor(rootBook()).isFailure).isTrue()
    }

    @Test
    fun `a root-level book in the library folder is ours`() = runBlocking {
        val source = connected(
            row(pathname = "/sdcard/Books/$ROOT_BOOK_NAME", filename = ROOT_BOOK_NAME, percent = "5000"),
        )

        assertThat(source.progressFor(rootBook()).getOrNull()?.percent).isWithin(0.001f).of(50f)
    }

    private suspend fun connected(vararg rows: Map<String, String?>): CoolReaderProgressSource {
        val source = CoolReaderProgressSource(databases = FakeDatabase(rows = rows.toList()))
        source.connect()
        return source
    }

    private fun source() = CoolReaderProgressSource(databases = FakeDatabase(rows = emptyList()))

    private fun row(
        pathname: String,
        filename: String,
        percent: String? = "1000",
        startPos: String? = "/body/x",
        timeStamp: String? = "100",
    ): Map<String, String?> = mapOf(
        "pathname" to pathname,
        "filename" to filename,
        "percent" to percent,
        "start_pos" to startPos,
        "time_stamp" to timeStamp,
    )

    private fun ourBook() = ProgressBookRef(
        bookId = "book-1",
        documentUri = DOCUMENT_URI,
        readerAddress = DOCUMENT_URI,
        fileName = BOOK_NAME,
        relativePath = RELATIVE_PATH,
        sizeBytes = 1_000L,
        librarySourceId = "source-1",
    )

    /** Книга в корне папки библиотеки: относительный путь — одно имя файла. */
    private fun rootBook() = ourBook().copy(fileName = ROOT_BOOK_NAME, relativePath = ROOT_BOOK_NAME)

    /** Фейк порта базы: `null` — «базу не открыть» (нет файла, нет доступа, мусор). */
    private class FakeDatabase(private val rows: List<Map<String, String?>>?) : ProgressDatabase {

        val queries = mutableListOf<AskedQuery>()

        override suspend fun open(databasePath: String): ProgressQuery? {
            if (rows == null) return null
            return object : ProgressQuery {
                override fun rows(sql: String, args: List<String>): List<Map<String, String?>> {
                    queries += AskedQuery(sql = sql, args = args)
                    return rows
                }

                override fun close() = Unit
            }
        }
    }

    private data class AskedQuery(val sql: String, val args: List<String>)

    private companion object {
        const val BOOK_NAME = "Книга.fb2.epub"
        const val ROOT_BOOK_NAME = "Книга-в-корне.fb2.epub"
        const val RELATIVE_PATH = "Лем/Книга.fb2.epub"
        const val BOOK_PATH = "/sdcard/Books/Лем/Книга.fb2.epub"
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC%2F%D0%9A%D0%BD%D0%B8%D0%B3%D0%B0.fb2.epub"
    }
}
