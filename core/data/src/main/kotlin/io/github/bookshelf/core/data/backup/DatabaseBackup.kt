package io.github.bookshelf.core.data.backup

import io.github.bookshelf.core.common.error.AppResult

/**
 * Копия базы и восстановление из неё.
 *
 * Зачем: база живёт в приватном каталоге приложения, и при удалении приложения пропадает вместе с
 * ней — вместе с полками, прогрессом чтения и настройками книг. Поэтому копия кладётся **в папку
 * библиотеки** (SAF), которая переживает переустановку и видна пользователю.
 */
interface DatabaseBackup {

    /**
     * Сделать копию базы в папку библиотеки.
     *
     * Копия снимается средствами SQLite (`VACUUM INTO`), а не копированием файла: файл, скопированный
     * во время записи, может оказаться битым, и «бэкап» стал бы ловушкой.
     *
     * @param automatic запуск по расписанию: подчиняется настройке `advanced.dbBackupEnabled`.
     *   Ручной запуск (по умолчанию) работает всегда.
     */
    suspend fun backupNow(automatic: Boolean = false): AppResult<BackupResult>

    /** Доступные копии, новые первыми. */
    suspend fun availableBackups(): AppResult<List<BackupEntry>>

    /**
     * Восстановить базу из копии.
     *
     * Перед подменой копия проверяется на целостность, а текущая база сохраняется отдельно: ошибка
     * выбора не должна стоить человеку библиотеки. После успеха приложение нужно перезапустить —
     * база подменена под работающим процессом.
     */
    suspend fun restore(entry: BackupEntry): AppResult<Unit>
}

/**
 * Итог создания копии.
 *
 * @param fileName имя файла копии в папке библиотеки.
 * @param sizeBytes размер копии.
 * @param removed сколько старых копий удалено по сроку хранения.
 */
data class BackupResult(
    val fileName: String,
    val sizeBytes: Long,
    val removed: Int,
)

/**
 * Копия, доступная для восстановления.
 *
 * @param documentUri адрес файла в дереве SAF: по нему копия читается.
 * @param fileName имя файла, как его видит пользователь.
 * @param createdAt время создания в миллисекундах (из имени файла).
 * @param sizeBytes размер копии; `null`, если провайдер его не отдал.
 */
data class BackupEntry(
    val documentUri: String,
    val fileName: String,
    val createdAt: Long,
    val sizeBytes: Long?,
)
