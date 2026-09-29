package io.github.bookshelf.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.database.BookshelfDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Снимок базы средствами SQLite.
 *
 * Почему не копирование файла: база работает в режиме WAL, и файл, скопированный во время записи,
 * может оказаться битым. Такой «бэкап» опаснее его отсутствия — человек узнает о проблеме только
 * тогда, когда попробует восстановиться.
 *
 * Основной путь — `VACUUM INTO`: SQLite сам создаёт целостную копию базы. На старых версиях
 * (Android до 11) оператора может не быть, поэтому есть откат: контрольная точка WAL, после которой
 * все данные лежат в основном файле, и копирование файла.
 */
@Singleton
class RoomDatabaseSnapshot @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: BookshelfDatabase,
) : DatabaseSnapshot {

    override fun snapshotTo(target: File): File {
        target.parentFile?.mkdirs()
        val created = runCatching { vacuumInto(target) }.isSuccess
        if (!created) copyDatabaseFile(target)
        return target
    }

    /**
     * `VACUUM INTO` — копия на уровне SQLite.
     *
     * Путь подставляется в оператор, а не в качестве параметра: `VACUUM INTO` параметров не
     * принимает, поэтому кавычка в пути экранируется удвоением.
     */
    private fun vacuumInto(target: File) {
        val path = target.absolutePath.replace("'", "''")
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '$path'")
        check(target.length() > 0L) { "SQLite создал пустую копию" }
    }

    /**
     * Откат для старых SQLite: контрольная точка и копирование файла.
     *
     * Контрольная точка переносит содержимое журнала в основной файл (`TRUNCATE` его ещё и
     * обрезает), после чего копия совпадает с базой на момент вызова.
     */
    private fun copyDatabaseFile(target: File) {
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        val source = context.getDatabasePath(BookshelfDatabase.NAME)
        check(source.exists()) { "Файл базы не найден: ${source.name}" }
        source.copyTo(target, overwrite = true)
    }

    /**
     * Проверка целостности копии.
     *
     * `null` — файл целый; иначе текст от SQLite (например, `database disk image is malformed`).
     * Проверка идёт на отдельном соединении только для чтения: саму базу приложения она не трогает.
     */
    override fun integrityMessage(file: File): String? = runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { check ->
            check.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst()) return@use "Пустой ответ проверки целостности"
                val result = cursor.getString(0)
                if (result.equals("ok", ignoreCase = true)) null else result
            }
        }
    }.getOrElse { error -> error.message ?: "Копия не открывается" }
}
