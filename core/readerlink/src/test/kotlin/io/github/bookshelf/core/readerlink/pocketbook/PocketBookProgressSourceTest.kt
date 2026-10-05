package io.github.bookshelf.core.readerlink.pocketbook

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
 * Прогресс книги из базы PocketBook (`PocketBookProgressSource`).
 *
 * Проверяется по живой базе с устройства (Bigme HiBreak, 05.10.2026): прогресс PocketBook хранит не
 * колонкой, а **тегами** книги — `doc.read_progress` (доля 0..1 строкой) и `doc.last_read_position`
 * (строка `pbr:/img?page=3&offs=1#epubcfi(/6/8!/4/2/4/2)`), а книга опознаётся по `Files.Name` и
 * `Paths.Path` (`/sdcard/Books/…` и `/storage/emulated/0/Books/…` — один и тот же файл под двумя
 * путями, поэтому строки приходится сводить по книге, а не брать первую попавшуюся).
 *
 * Нулевой процент вместо «данных нет» здесь особенно легко получить по ошибке: в базе лежит и
 * `"0"`, и пустая строка, и текст. Показывать ноль нельзя — на полке это «книга не начата».
 */
class PocketBookProgressSourceTest {

    @Test
    fun `the source names itself as the pocketBook channel`() = runBlocking {
        val source = source()

        assertThat(source.id).isEqualTo(ProgressSourceId.POCKETBOOK_DB)
        // PocketBook наружу «что открыто» не отдаёт: его читают по конкретной книге.
        assertThat(source.knowsOpenBook).isFalse()
        assertThat(source.openedBook()).isNull()
    }

    @Test
    fun `the share from the tags becomes percent with its position`() = runBlocking {
        val source = connected(
            progressRow(share = "0.00943396240472794"),
            positionRow(position = "pbr:/img?page=3&offs=1#epubcfi(/6/8!/4/2/4/2)"),
        )

        val snapshot = source.progressFor(ourBook()).getOrNull()

        // Доля 0.00943 — это 0,94 %.
        assertThat(snapshot?.percent).isWithin(0.001f).of(0.9434f)
        assertThat(snapshot?.locatorJson).isEqualTo("pbr:/img?page=3&offs=1#epubcfi(/6/8!/4/2/4/2)")
    }

    @Test
    fun `both tags are requested, and only they`() = runBlocking {
        val database = FakeDatabase(rows = listOf(progressRow(share = "0.5")))
        val source = PocketBookProgressSource(databases = database)
        source.connect()

        // Спрашиваем ровно два тега: тащить из базы все теги книги (обложки, шрифты, режим) незачем.
        assertThat(database.queries.single().args).containsExactly(READ_PROGRESS_TAG, LAST_POSITION_TAG)
        source.disconnect()
    }

    @Test
    fun `the same book under two paths is read once`() = runBlocking {
        // Так выглядит живая база: один и тот же файл лежит под `/storage/emulated/0/Books/…`
        // и под `/sdcard/Books/…`.
        val source = connected(
            progressRow(share = "0.42", path = "/storage/emulated/0/Books/Лем/"),
            progressRow(share = "0.42", path = "/sdcard/Books/Лем/"),
            positionRow(position = "pbr:/img?page=1", path = "/storage/emulated/0/Books/Лем/"),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(42f)
    }

    @Test
    fun `the freshest book wins when two share the name`() = runBlocking {
        val source = connected(
            progressRow(share = "0.10", timeEdt = "100", itemId = "1"),
            progressRow(share = "0.70", timeEdt = "200", itemId = "2"),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(70f)
    }

    @Test
    fun `another book is not our progress`() = runBlocking {
        val source = connected(
            progressRow(share = "0.5", name = "Другая.epub", path = "/sdcard/Books/Другая.epub"),
        )

        val result = source.progressFor(ourBook())

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `same-named books with unrelated paths are not guessed`() = runBlocking {
        // Разные книги (`Items.OID`), одинаковое имя файла, пути не совпали с нашим: выбрать
        // «первую» — значит записать прогресс наугад.
        val source = connected(
            progressRow(share = "0.1", path = "/sdcard/Other/", itemId = "1"),
            progressRow(share = "0.2", path = "/mnt/media_rw/1/", itemId = "2"),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a percent above the whole book is capped`() = runBlocking {
        // Доля приходит из чужой базы: и 1.5, и «мусор» там возможны, а «150 %» на полке — нет.
        val source = connected(progressRow(share = "1.5"))

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isEqualTo(100f)
    }

    @Test
    fun `an unreadable share does not become zero`() = runBlocking {
        val source = connected(progressRow(share = ""))

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a book without a progress tag gives nothing`() = runBlocking {
        // У книги есть только позиция, а доли нет: показывать 0 % нельзя.
        val source = connected(positionRow(position = "pbr:/img?page=3"))

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a database we cannot read gives no progress`() = runBlocking {
        val source = PocketBookProgressSource(databases = FakeDatabase(rows = null))

        assertThat(source.connect()).isFalse()
        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `an empty database gives no progress`() = runBlocking {
        assertThat(connected().progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a book moved inside the library is found by its size`() = runBlocking {
        // Книгу переложили, и путь читалки больше не сходится с нашим. У PocketBook есть вторая
        // примета — размер файла (`Files.Len`), и он совпадает точно.
        val source = connected(progressRow(share = "0.3", path = "/sdcard/Другое/", size = BOOK_SIZE))

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(30f)
    }

    @Test
    fun `same-named books are told apart by size`() = runBlocking {
        // Ни один путь не наш, но размер сошёлся ровно у одной книги — это она.
        val source = connected(
            progressRow(share = "0.1", path = "/sdcard/Одно/", size = BOOK_SIZE + 1, itemId = "1"),
            progressRow(share = "0.2", path = "/sdcard/Другое/", size = BOOK_SIZE, itemId = "2"),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(20f)
    }

    @Test
    fun `two candidates of our size are not guessed`() = runBlocking {
        // Два одинаковых размера ничего не доказывают: писать процент наугад нельзя.
        val source = connected(
            progressRow(share = "0.1", path = "/sdcard/Одно/", size = BOOK_SIZE, itemId = "1"),
            progressRow(share = "0.2", path = "/sdcard/Другое/", size = BOOK_SIZE, itemId = "2"),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a candidate without a size is not taken by size`() = runBlocking {
        // У старых записей PocketBook размера может не быть — тогда приметы нет, и это «нет данных».
        val source = connected(progressRow(share = "0.3", path = "/sdcard/Другое/", size = null))

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a root-level book is not confused with a same-named file elsewhere`() = runBlocking {
        // Книга в корне папки библиотеки: нужна не только концовка пути, но и сама папка библиотеки.
        val source = connected(
            progressRow(share = "0.5", name = ROOT_BOOK_NAME, path = "/sdcard/Other/"),
        )

        assertThat(source.progressFor(rootBook()).isFailure).isTrue()
    }

    private suspend fun connected(vararg rows: Map<String, String?>): PocketBookProgressSource {
        val source = PocketBookProgressSource(databases = FakeDatabase(rows = rows.toList()))
        source.connect()
        return source
    }

    private fun source() = PocketBookProgressSource(databases = FakeDatabase(rows = emptyList()))

    private fun progressRow(
        share: String?,
        name: String = BOOK_NAME,
        path: String = BOOK_FOLDER,
        title: String = BOOK_TITLE,
        timeEdt: String? = "100",
        itemId: String = "6",
        size: Long? = null,
    ): Map<String, String?> = tagRow(
        tag = READ_PROGRESS_TAG,
        value = share,
        name = name,
        path = path,
        title = title,
        timeEdt = timeEdt,
        itemId = itemId,
        size = size,
    )

    private fun positionRow(
        position: String?,
        name: String = BOOK_NAME,
        path: String = BOOK_FOLDER,
        title: String = BOOK_TITLE,
        timeEdt: String? = "100",
        itemId: String = "6",
        size: Long? = null,
    ): Map<String, String?> = tagRow(
        tag = LAST_POSITION_TAG,
        value = position,
        name = name,
        path = path,
        title = title,
        timeEdt = timeEdt,
        itemId = itemId,
        size = size,
    )

    private fun tagRow(
        tag: String,
        value: String?,
        name: String,
        path: String,
        title: String,
        timeEdt: String?,
        itemId: String,
        size: Long? = null,
    ): Map<String, String?> = mapOf(
        "item_id" to itemId,
        "tag" to tag,
        "val" to value,
        "name" to name,
        "path" to path,
        "title" to title,
        "time_edt" to timeEdt,
        "len" to size?.toString(),
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
        const val READ_PROGRESS_TAG = "doc.read_progress"
        const val LAST_POSITION_TAG = "doc.last_read_position"
        const val BOOK_NAME = "Книга.fb2.epub"
        /** Размер книги из `ourBook()`: с ним сравнивается `Files.Len`. */
        const val BOOK_SIZE = 1_000L
        const val ROOT_BOOK_NAME = "Книга-в-корне.fb2.epub"
        const val BOOK_TITLE = "Книга"
        const val RELATIVE_PATH = "Лем/Книга.fb2.epub"
        const val BOOK_FOLDER = "/sdcard/Books/Лем/"
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC%2F%D0%9A%D0%BD%D0%B8%D0%B3%D0%B0.fb2.epub"
    }
}
