package io.github.bookshelf.core.data.reader

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.common.log.debug
import io.github.bookshelf.core.readerlink.progress.ProgressDatabase
import io.github.bookshelf.core.readerlink.progress.ProgressQuery
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Чтение базы читалки по копии в кэше (`SQLiteDatabase`).
 *
 * Почему копия, а не сама база:
 * * читалка держит файл открытым и пишет в него — читать «живой» файл значит увидеть половину
 *   транзакции;
 * * прогресс PocketBook лежит в журнале WAL: без `-wal` и `-shm` часть записей просто не видна, а
 *   копия без журнала — это другое состояние базы, а не «то же самое минус свежие строки»;
 * * открывать чужой файл на запись мы не имеем права — SQLite же в режиме WAL без записи не
 *   работает, ему нужен `-shm`.
 *
 * Каталог копии уникален на каждое открытие: две синхронизации внахлёст не должны перемешивать
 * файлы. При закрытии каталог удаляется целиком; повторное закрытие безопасно.
 */
@Singleton
class ForeignSqliteDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : ProgressDatabase {

    override suspend fun open(databasePath: String): ProgressQuery? = withContext(dispatchers.io) {
        runCatching { openCopy(databasePath) }.getOrNull()
    }

    private fun openCopy(databasePath: String): SqliteQuery? {
        val source = File(databasePath)
        if (!source.isFile) return null
        val workDir = File(cacheDir(), UUID.randomUUID().toString()).apply { mkdirs() }
        return try {
            val copy = copyWithJournals(source, workDir)
            SqliteQuery(
                database = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY),
                workDir = workDir,
            )
        } catch (error: IOException) {
            // Файл мог остаться недописанным: «данных нет» — не сбой синхронизации, но причину
            // пишем в журнал: без неё «прогресс не появился» не разобрать.
            logger.debug("чужая база $databasePath не скопирована: ${error.message}", error)
            workDir.deleteRecursively()
            null
        } catch (error: SQLiteException) {
            // Мусорный файл или база чужой версии: читать нечего, и падать из-за чужого файла нельзя.
            logger.debug("чужая база $databasePath не открылась: ${error.message}", error)
            workDir.deleteRecursively()
            null
        }
    }

    /**
     * Скопировать базу рядом с её журналами.
     *
     * Имена журналов важны: SQLite сам находит `-wal`, `-shm` и `-journal` по имени файла базы,
     * поэтому копии обязаны называться так же и лежать в одном каталоге.
     */
    private fun copyWithJournals(source: File, workDir: File): File {
        val copy = File(workDir, source.name)
        source.copyTo(copy, overwrite = true)
        JOURNAL_SUFFIXES.forEach { suffix ->
            File("${source.path}$suffix")
                .takeIf { it.isFile }
                ?.copyTo(File("${copy.path}$suffix"), overwrite = true)
        }
        return copy
    }

    private fun cacheDir(): File = File(context.cacheDir, COPY_DIR).apply { mkdirs() }

    /**
     * Открытая копия базы.
     *
     * Значения читаются по типу колонки, а не `getString`: так число приходит числом, а `NULL`
     * остаётся `NULL` — адаптеры читалок различают «нет записи» и «запись с нулём».
     */
    private class SqliteQuery(
        private val database: SQLiteDatabase,
        private val workDir: File,
    ) : ProgressQuery {

        private var closed = false

        override fun rows(sql: String, args: List<String>): List<Map<String, String?>> = runCatching {
            database.rawQuery(sql, args.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toRow())
                }
            }
        }.getOrDefault(emptyList())

        override fun close() {
            if (closed) return
            closed = true
            runCatching { database.close() }
            workDir.deleteRecursively()
        }

        private fun Cursor.toRow(): Map<String, String?> = buildMap {
            for (index in 0 until columnCount) {
                put(getColumnName(index), valueAt(index))
            }
        }

        private fun Cursor.valueAt(index: Int): String? = when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT -> getDouble(index).toString()
            else -> getString(index)
        }
    }

    /**
     * Константы открыты тестам: `COPY_DIR` — место копий, по нему проверяется их уборка
     * (`ForeignSqliteDatabaseTest`), и дублировать строку в тесте значило бы проверять не то место.
     */
    internal companion object {

        /** Подкаталог кэша: копии чужих баз живут вместе и убираются при закрытии. */
        const val COPY_DIR = "foreign-db"

        /** Журналы, без которых копия базы — не то же состояние, что у читалки. */
        val JOURNAL_SUFFIXES = listOf("-wal", "-shm", "-journal")
    }
}
