package io.github.bookshelf.core.readerlink.librera

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
 * Прогресс книги из файлов Librera (`LibreraProgressSource`).
 *
 * Librera держит прогресс не в базе, а в JSON на общей памяти:
 * `/sdcard/Librera/profile.Librera/device.<модель>/app-Progress.json` — объект «имя файла → запись», где
 * `p` — доля 0..1, `t` — время записи, `pt` — фрагмент текста. Рядом `app-Recent.json` — список
 * недавних книг с **реальными путями** (`internal-storage:/Books/…` и `/sdcard/Books/…`).
 *
 * Имя каталога зависит от модели устройства (`device.HiBreak`), поэтому каталог ищется, а не
 * зашивается в код; тесты работают на настоящих файлах с устройства (05.10.2026) — они лежат в
 * `core/readerlink/src/test/resources/librera`.
 *
 * Ключ у Librera — **имя файла**, а не путь, поэтому по `app-Recent.json` проверяется, нашей ли книге
 * принадлежит запись: две книги с одинаковым именем в Librera неразличимы, и записывать процент
 * наугад нельзя. Адреса позиции у Librera нет (только прокрутка `x`/`y` и фрагмент текста), поэтому
 * в снимок идёт один процент.
 */
class LibreraProgressSourceTest {

    @Test
    fun `the source names itself as the librera channel`() = runBlocking {
        val source = connected(withFixtures())

        assertThat(source.id).isEqualTo(ProgressSourceId.LIBRERA_JSON)
        // Librera наружу «что открыто» не отдаёт: её читают по конкретной книге.
        assertThat(source.knowsOpenBook).isFalse()
        assertThat(source.openedBook()).isNull()
    }

    @Test
    fun `the real fixture gives the share of the book`() = runBlocking {
        val source = connected(withFixtures())

        val snapshot = source.progressFor(ourBook()).getOrNull()

        // В файле с устройства: "p": 0.02853067 — это 2,853 %.
        assertThat(snapshot?.percent).isWithin(0.001f).of(2.853f)
        // Адреса позиции у Librera нет: `pt` — фрагмент текста, а не место в книге.
        assertThat(snapshot?.locatorJson).isNull()
    }

    @Test
    fun `the device folder is discovered, not hardcoded`(): Unit = runBlocking {
        val storage = withFixtures()

        val source = LibreraProgressSource(storage)
        source.connect()

        // Каталог смотрит, а имя `device.<модель>` находит: у другой модели оно другое.
        assertThat(storage.listedDirectories).contains(PROFILE_DIRECTORY)
        assertThat(storage.readPaths).containsExactly(
            progressPath("device.HiBreak"),
            recentPath("device.HiBreak"),
        )
        source.disconnect()
    }

    @Test
    fun `among device folders the freshest record wins`() = runBlocking {
        // Папок `device.*` может оказаться несколько (переименование модели): берём не первую,
        // а самую свежую запись.
        val storage = FakeStorage(
            files = mapOf(
                progressPath("device.Old") to """{"$BOOK_NAME": {"p": 0.1, "t": 100}}""",
                progressPath("device.HiBreak") to """{"$BOOK_NAME": {"p": 0.9, "t": 200}}""",
            ),
            directories = mapOf(PROFILE_DIRECTORY to listOf("device.Old", "device.HiBreak")),
        )
        val source = connected(storage)

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(90f)
    }

    @Test
    fun `a share above the whole book is capped`() = runBlocking {
        val source = connected(
            storage(progress = """{"$BOOK_NAME": {"p": 1.5, "t": 1}}"""),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isEqualTo(100f)
    }

    @Test
    fun `a zero share is kept as a start of reading`() = runBlocking {
        // `p = 0` — книгу открыли и не продвинулись; это не «нет данных», иначе книга начнёт
        // выглядеть неначатой после того, как её открыли.
        val source = connected(storage(progress = """{"$BOOK_NAME": {"p": 0, "t": 1}}"""))

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isEqualTo(0f)
    }

    @Test
    fun `a record without a share gives nothing`() = runBlocking {
        val source = connected(storage(progress = """{"$BOOK_NAME": {"t": 1, "x": 10}}"""))

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `broken json is not an error`() = runBlocking {
        val storage = storage(progress = "это не json")
        val source = LibreraProgressSource(storage)

        // Файл может быть недописан: «данных нет» — не сбой синхронизации.
        assertThat(source.connect()).isTrue()
        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
        source.disconnect()
    }

    @Test
    fun `an empty file is not an error either`() = runBlocking {
        val source = connected(storage(progress = ""))

        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `no librera folder means no source`() = runBlocking {
        // Читалки может не быть вовсе, а на flavor play нет доступа ко всей памяти: это
        // «источник недоступен», а не «книга не начата».
        val source = LibreraProgressSource(FakeStorage(files = emptyMap(), directories = emptyMap()))

        assertThat(source.connect()).isFalse()
        assertThat(source.progressFor(ourBook()).isFailure).isTrue()
    }

    @Test
    fun `a book known to librera under another path is not ours`() = runBlocking {
        // Имя файла занято другой книгой (так бывает у одноимённых файлов), и наш путь в списке
        // недавних не встречается: записывать этот процент нам нельзя.
        val source = connected(
            storage(
                progress = """{"$BOOK_NAME": {"p": 0.5, "t": 1}}""",
                recent = """[{"path": "/sdcard/Other/$BOOK_NAME", "time": 1}]""",
            ),
        )

        val result = source.progressFor(ourBook())

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `our own path in the recent list confirms the record`() = runBlocking {
        val source = connected(
            storage(
                progress = """{"$BOOK_NAME": {"p": 0.5, "t": 1}}""",
                recent = """[{"path": "internal-storage:/Books/$RELATIVE_PATH", "time": 1}]""",
            ),
        )

        assertThat(source.progressFor(ourBook()).getOrNull()?.percent).isWithin(0.001f).of(50f)
    }

    private suspend fun connected(storage: FakeStorage): LibreraProgressSource {
        val source = LibreraProgressSource(storage)
        source.connect()
        return source
    }

    /** Хранилище с настоящими файлами Librera, снятыми с устройства. */
    private fun withFixtures(): FakeStorage = FakeStorage(
        files = mapOf(
            progressPath("device.HiBreak") to fixture("librera/app-Progress.json"),
            recentPath("device.HiBreak") to fixture("librera/app-Recent.json"),
        ),
        directories = mapOf(PROFILE_DIRECTORY to listOf("device.HiBreak")),
    )

    /** Хранилище с одной папкой устройства и заданными файлами прогресса и недавних книг. */
    private fun storage(progress: String, recent: String? = null): FakeStorage = FakeStorage(
        files = buildMap {
            put(progressPath("device.HiBreak"), progress)
            recent?.let { put(recentPath("device.HiBreak"), it) }
        },
        directories = mapOf(PROFILE_DIRECTORY to listOf("device.HiBreak")),
    )

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "нет фикстуры $name" }
            .use { it.readBytes().decodeToString() }

    private fun progressPath(device: String) = "$PROFILE_DIRECTORY/$device/$PROGRESS_FILE"

    private fun recentPath(device: String) = "$PROFILE_DIRECTORY/$device/$RECENT_FILE"

    private fun ourBook() = ProgressBookRef(
        bookId = "book-1",
        documentUri = DOCUMENT_URI,
        readerAddress = DOCUMENT_URI,
        fileName = BOOK_NAME,
        relativePath = RELATIVE_PATH,
        sizeBytes = 1_000L,
        librarySourceId = "source-1",
    )

    /** Фейк хранилища: только те файлы и каталоги, что заданы тестом. */
    private class FakeStorage(
        private val files: Map<String, String>,
        private val directories: Map<String, List<String>>,
    ) : ProgressStorage {

        val readPaths = mutableListOf<String>()
        val listedDirectories = mutableListOf<String>()

        override suspend fun read(path: String): String? {
            readPaths += path
            return files[path]
        }

        override suspend fun children(directory: String): List<String> {
            listedDirectories += directory
            return directories[directory].orEmpty()
        }
    }

    private companion object {
        const val PROFILE_DIRECTORY = "/sdcard/Librera/profile.Librera"
        const val PROGRESS_FILE = "app-Progress.json"
        const val RECENT_FILE = "app-Recent.json"
        const val BOOK_NAME = "Zlotnikov_Vselennaya-neudachnikov_1_Vselennaya-neudachnikov.563756.fb2.epub"
        const val RELATIVE_PATH = BOOK_NAME
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2F"
    }
}
