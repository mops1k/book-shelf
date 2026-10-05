package io.github.bookshelf.core.readerlink.xreader

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ProgressStorage
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Прогресс книги из кэша XReader (`XReaderProgressSource`).
 *
 * Проверяется по настоящему файлу с устройства (Bigme HiBreak, 05.10.2026):
 * `/sdcard/XRZBookCache/V3/jsec2e9a84641a7782291186e91313969e` — это **gzip**, внутри объект, а в нём
 * `bookStateInfo` — вложенная строка JSON с `bookPath`, `bookName`, `currentPage` (10), `totalPage`
 * (1099) и `bookEndTime`. Процент считается как `currentPage / totalPage` (у XReader это и есть
 * показанное «10 из 1099»), а `totalPage = 0` — не ноль процентов, а «данных нет»: делить нельзя.
 *
 * Кэш читается байтами: как текст gzip не прочитать, и разбор обязан это учитывать.
 */
class XReaderProgressSourceTest {

    @Test
    fun `the source names itself as the xReader channel`() = runBlocking {
        val source = connected(withFixture())

        assertThat(source.id).isEqualTo(ProgressSourceId.XREADER_CACHE)
        // XReader наружу «что открыто» не отдаёт: его читают по конкретной книге.
        assertThat(source.knowsOpenBook).isFalse()
        assertThat(source.openedBook()).isNull()
    }

    @Test
    fun `the real cache file gives the page of the book`() = runBlocking {
        val source = connected(withFixture())

        val snapshot = source.progressFor(ourBook()).getOrNull()

        // В файле с устройства: currentPage = 10, totalPage = 1099 → 0,91 %.
        assertThat(snapshot?.percent).isWithin(0.001f).of(0.9099f)
        // Номер страницы у XReader настоящий, в отличие от доли у Librera, поэтому он идёт в снимок.
        assertThat(snapshot?.pageIndex).isEqualTo(10)
    }

    @Test
    fun `cache files are found by prefix and read as bytes`(): Unit = runBlocking {
        val storage = withFixture()

        val source = XReaderProgressSource(storage)
        source.connect()

        assertThat(storage.listedDirectories).contains(CACHE_DIRECTORY)
        assertThat(storage.readPaths).containsExactly(CACHE_FILE)
        source.disconnect()
    }

    @Test
    fun `an uncompressed cache file is read too`() = runBlocking {
        // Версии XReader отличаются: файл может лежать и без сжатия, и это не повод терять прогресс.
        val source = connected(
            FakeStorage(
                files = mapOf(CACHE_FILE to cacheJson().toByteArray()),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.pageIndex).isEqualTo(10)
    }

    @Test
    fun `the newest record wins when the book has several`() = runBlocking {
        val older = cacheJson(endTime = "2026-10-01 10:00:00", page = 5)
        val newer = cacheJson(endTime = "2026-10-05 15:56:38", page = 40)
        val source = connected(
            FakeStorage(
                // Одна и та же книга, два состояния: берём то, которое закончили читать позже.
                files = mapOf(
                    "$CACHE_DIRECTORY/jsaaa" to gzip(older),
                    "$CACHE_DIRECTORY/jsbbb" to gzip(newer),
                ),
                directories = mapOf(CACHE_DIRECTORY to listOf("jsaaa", "jsbbb")),
            ),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.pageIndex).isEqualTo(40)
    }

    @Test
    fun `a zero page count gives nothing instead of zero percent`() = runBlocking {
        // `totalPage = 0` — книга ещё не разобрана читалкой: 0 % на полке означал бы «не начата».
        val source = connected(
            FakeStorage(
                files = mapOf(CACHE_FILE to gzip(cacheJson(totalPage = 0))),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        val result = source.progressFor(ourBook())

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `a page beyond the book is capped`() = runBlocking {
        val source = connected(
            FakeStorage(
                files = mapOf(CACHE_FILE to gzip(cacheJson(page = 2_000))),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isEqualTo(100f)
    }

    @Test
    fun `broken gzip is not an error`() = runBlocking {
        val source = connected(
            FakeStorage(
                files = mapOf(CACHE_FILE to byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x01, 0x02)),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `broken json is not an error either`() = runBlocking {
        val source = connected(
            FakeStorage(
                files = mapOf(CACHE_FILE to "это не json".toByteArray()),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `another book is not our progress`() = runBlocking {
        val source = connected(
            FakeStorage(
                files = mapOf(
                    CACHE_FILE to gzip(cacheJson(path = "/sdcard/Books/Лем/Другая.fb2.epub")),
                ),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a copy made by xReader is not guessed`() = runBlocking {
        // По SAF-запуску XReader копирует книгу в свой каталог со тем же именем файла. Такую запись мы
        // НЕ принимаем по имени: на устройстве 05.10.2026 копий нет (XReader запускается по реальному
        // пути), а угадать по имени — значит записать процент чужой одноимённой книге. Маршрутизацию
        // копий через таблицу `book_copies` решаем отдельной задачей, когда копия появится в кэше.
        val source = connected(
            FakeStorage(
                files = mapOf(
                    CACHE_FILE to gzip(
                        cacheJson(path = "/sdcard/xReader/thirdparty/primary/Книга-в-корне.fb2.epub"),
                    ),
                ),
                directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
            ),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `no cache folder means no source`() = runBlocking {
        // Читалки может не быть вовсе, а на flavor play нет доступа ко всей памяти.
        val source = XReaderProgressSource(FakeStorage(files = emptyMap(), directories = emptyMap()))

        assertThat(source.connect()).isFalse()
        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a cache folder without state files gives no progress`() = runBlocking {
        val source = connected(
            FakeStorage(
                files = mapOf("$CACHE_DIRECTORY/notes.txt" to "не состояние книги".toByteArray()),
                directories = mapOf(CACHE_DIRECTORY to listOf("notes.txt")),
            ),
        )

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    private suspend fun connected(storage: FakeStorage): XReaderProgressSource {
        val source = XReaderProgressSource(storage)
        source.connect()
        return source
    }

    /** Хранилище с настоящим файлом кэша, снятым с устройства. */
    private fun withFixture(): FakeStorage = FakeStorage(
        files = mapOf(CACHE_FILE to fixture("xreader/reader-cache.gz")),
        directories = mapOf(CACHE_DIRECTORY to listOf(FILE_NAME)),
    )

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "нет фикстуры $name" }
            .use { it.readBytes() }

    /** Внутреннее состояние книги так, как его пишет XReader: `bookStateInfo` — вложенная строка. */
    private fun cacheJson(
        path: String = "/sdcard/Books/$BOOK_NAME",
        page: Int = 10,
        totalPage: Int = 1_099,
        endTime: String = "2026-10-05 15:56:38",
    ): String {
        val inner = """{"bookPath":"$path","bookName":"$BOOK_NAME","currentPage":$page,""" +
            """"totalPage":$totalPage,"bookEndTime":"$endTime"}"""
        val escaped = inner.replace("\"", "\\\"")
        return """{"BookZoom":1.0,"bookId":"ec2e9a","bookStateInfo":"$escaped"}"""
    }

    private fun gzip(text: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(output).use { it.write(text.toByteArray()) }
        return output.toByteArray()
    }

    private fun ourBook() = ProgressBookRef(
        bookId = "book-1",
        documentUri = DOCUMENT_URI,
        readerAddress = DOCUMENT_URI,
        fileName = BOOK_NAME,
        relativePath = BOOK_NAME,
        sizeBytes = 1_000L,
        librarySourceId = "source-1",
    )

    /** Фейк хранилища: только те файлы и каталоги, что заданы тестом. */
    private class FakeStorage(
        private val files: Map<String, ByteArray>,
        private val directories: Map<String, List<String>>,
    ) : ProgressStorage {

        val readPaths = mutableListOf<String>()
        val listedDirectories = mutableListOf<String>()

        override suspend fun readBytes(path: String): ByteArray? {
            readPaths += path
            return files[path]
        }

        override suspend fun children(directory: String): List<String> {
            listedDirectories += directory
            return directories[directory].orEmpty()
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "/sdcard/XRZBookCache/V3"
        const val FILE_NAME = "jsec2e9a84641a7782291186e91313969e"
        const val CACHE_FILE = "$CACHE_DIRECTORY/$FILE_NAME"
        const val BOOK_NAME = "Zlotnikov_Vselennaya-neudachnikov_1_Vselennaya-neudachnikov.563756.fb2.epub"
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2F"
    }
}
