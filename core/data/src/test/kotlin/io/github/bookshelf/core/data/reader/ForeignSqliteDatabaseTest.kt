package io.github.bookshelf.core.data.reader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.RecordingAppLogger
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Чтение чужой базы читалки (`ForeignSqliteDatabase`).
 *
 * Базы CoolReader и PocketBook лежат на общей памяти, и в них — прогресс чтения. Проверяется то,
 * из-за чего чтение легко «потерять»:
 * * **журнал**: у PocketBook активен WAL, и без `-wal` последние записи прогресса не видны — тест
 *   оставляет строку в журнале (соединение пишущей стороны не закрыто) и требует, чтобы читатель её
 *   увидел;
 * * **чужие файлы неприкосновенны**: читалка держит базу открытой, поэтому работаем с копией;
 * * **отсутствие базы и мусорный файл** — это «прогресса нет» (`null`/пусто), а не ошибка: файла
 *   может не быть, а на flavor `play` доступа ко всей памяти нет вовсе.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ForeignSqliteDatabaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = ForeignSqliteDatabase(context, TestAppDispatchers(), RecordingAppLogger())

    @Test
    fun `a row that lives only in the write-ahead log is still read`(): Unit = runBlocking {
        val path = folder.newFile("cr3db.sqlite").absolutePath
        val writer = writeWithWal(path)
        try {
            val query = database.open(path)
            val rows = query?.rows("select name from bookmark") ?: emptyList()
            query?.close()

            assertThat(rows).containsExactly(mapOf("name" to "Солярис"))
        } finally {
            writer.close()
        }
    }

    @Test
    fun `the reader database is not modified and the copies are removed`(): Unit = runBlocking {
        val dir = folder.newFolder("cr3")
        val path = File(dir, "cr3db.sqlite").absolutePath
        val writer = writeWithWal(path)
        try {
            val before = File(path).readBytes()
            val neighboursBefore = dir.list()?.toList().orEmpty().sorted()

            val query = database.open(path)
            query?.rows("select name from bookmark")
            query?.close()

            // Файл читалки остаётся байт в байт: менять чужие данные мы не имеем права.
            assertThat(File(path).readBytes()).isEqualTo(before)
            // Рядом с базой читалки ничего не появилось и не исчезло (журналы WAL пишет сама читалка).
            assertThat(dir.list()?.toList().orEmpty().sorted()).isEqualTo(neighboursBefore)
            // И временных копий в кэше после закрытия не остаётся — иначе кэш распухнет.
            assertThat(cacheCopies()).isEmpty()
        } finally {
            writer.close()
        }
    }

    /** Каталоги копий в кэше: после закрытия их быть не должно. */
    private fun cacheCopies(): List<String> =
        File(context.cacheDir, ForeignSqliteDatabase.COPY_DIR).list()?.toList().orEmpty()

    @Test
    fun `a missing database is not an error`() = runBlocking {
        val path = folder.newFolder("пусто").let { File(it, "cr3db.sqlite").absolutePath }

        assertThat(database.open(path)).isNull()
    }

    @Test
    fun `a broken file is not an error either`(): Unit = runBlocking {
        // Так выглядит не база, а мусор: читалка могла не дозаписать файл, и падать из-за этого
        // синхронизация не должна.
        val path = folder.newFile("cr3db.sqlite").apply { writeText("это не база") }.absolutePath

        val query = database.open(path)
        val rows = query?.rows("select name from bookmark") ?: emptyList()

        assertThat(rows).isEmpty()
        query?.close()
    }

    @Test
    fun `arguments are substituted, not concatenated`() = runBlocking {
        val path = folder.newFile("cr3db.sqlite").absolutePath
        val writer = writeWithWal(path)
        try {
            val query = database.open(path)
            // Значение с кавычкой не должно ломать запрос: параметры подставляются, а не склеиваются.
            val rows = query?.rows("select name from bookmark where name = ?", listOf("Солярис"))
            query?.close()

            assertThat(rows).hasSize(1)
        } finally {
            writer.close()
        }
    }

    /**
     * Создать базу и оставить строку в журнале WAL.
     *
     * Соединение намеренно не закрывается: закрытие сделало бы контрольную точку и перенесло данные
     * в основной файл — тогда тест перестал бы проверять именно чтение журнала.
     */
    private fun writeWithWal(path: String): SQLiteDatabase {
        val writer = SQLiteDatabase.openOrCreateDatabase(File(path), null)
        writer.enableWriteAheadLogging()
        writer.execSQL("create table bookmark (name text)")
        writer.execSQL("insert into bookmark (name) values ('Солярис')")
        return writer
    }
}
