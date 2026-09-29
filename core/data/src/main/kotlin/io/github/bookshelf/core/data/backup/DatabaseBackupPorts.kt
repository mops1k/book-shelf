package io.github.bookshelf.core.data.backup

import io.github.bookshelf.core.common.error.AppResult
import java.io.File

/**
 * Снимок базы: целостная копия на диске.
 *
 * Отдельным портом, потому что снимок — это знание про SQLite (`VACUUM INTO` вместо копирования
 * файла, проверка `integrity_check`), а оркестрация копий — про папки и сроки хранения. Так
 * «сколько копий хранить» и «что делать с битым файлом» проверяются тестами без базы.
 */
interface DatabaseSnapshot {

    /**
     * Создать копию базы в файле [target].
     *
     * Исключение означает, что снимок не получился: копия, снятая копированием файла во время
     * записи, может оказаться битой, поэтому сбой лучше показать, чем сохранить «бэкап»-ловушку.
     */
    fun snapshotTo(target: File): File

    /** Текст ошибки целостности файла; `null` — файл целый. */
    fun integrityMessage(file: File): String?
}

/**
 * Хранилище копий: папка библиотеки через SAF.
 *
 * Порт нужен, чтобы правила хранения (сколько копий, что удалять, как читать) проверялись без
 * настоящего дерева документов, а работа с SAF осталась тонким адаптером.
 */
interface BackupStore {

    /** Копии в папке, в произвольном порядке: сортирует вызывающий. */
    suspend fun list(treeUri: String): AppResult<List<BackupEntry>>

    /** Записать копию в [folder] дерева [treeUri]. */
    suspend fun save(
        treeUri: String,
        folder: String,
        fileName: String,
        bytes: ByteArray,
    ): AppResult<Unit>

    /** Прочитать копию во временный файл. */
    suspend fun read(entry: BackupEntry): AppResult<File>

    /** Удалить копию. */
    suspend fun remove(entry: BackupEntry): AppResult<Unit>

    /**
     * Подменить базу приложения файлом копии.
     *
     * Отдельным шагом, а не «скопировать файл»: подмена требует закрыть работающую базу и убрать
     * её журнал (`-wal`/`-shm`), иначе SQLite увидит старые данные.
     */
    fun replaceDatabase(file: File): AppResult<Unit>
}
