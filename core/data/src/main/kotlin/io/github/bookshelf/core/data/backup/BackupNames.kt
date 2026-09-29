package io.github.bookshelf.core.data.backup

/**
 * Имена копий базы.
 *
 * Общие для сервиса и адаптера хранилища: копия называется одинаково и когда создаётся, и когда
 * ищется в папке библиотеки. Время создания закодировано в имени, а не берётся из свойств файла:
 * `lastModified` провайдеры отдают по-разному (а некоторые не отдают вовсе), а имя сохраняет
 * порядок копий при любом провайдере.
 */
internal object BackupNames {

    /** Папка внутри библиотеки: копии не мешают книгам и не индексируются сканом. */
    const val FOLDER = ".bookshelf"

    const val PREFIX = "bookshelf-backup-"

    const val SUFFIX = ".db"

    /**
     * Сколько копий хранить: это страховка, а не архив.
     *
     * Пять — предел, названный Александром 29.09.2026. Лишнюю копию можно удалить и вручную
     * (кнопка у каждой копии в «Хранилище и кэш»), поэтому предел — не единственный способ
     * избавиться от старых копий.
     */
    const val KEEP = 5

    /** Имя копии для момента [createdAt]. */
    fun fileNameFor(createdAt: Long): String = "$PREFIX$createdAt$SUFFIX"

    /** Копия ли это по имени файла. */
    fun isBackup(fileName: String): Boolean =
        fileName.startsWith(PREFIX) && fileName.endsWith(SUFFIX)

    /**
     * Время создания из имени; `null` — имя не похоже на копию или время в нём не число.
     *
     * `null` здесь не ошибка: в папке могут лежать чужие файлы, и «копия без времени» сортировалась
     * бы как самая старая, то есть удалялась первой. Лучше её не считать копией вовсе.
     */
    fun createdAtOf(fileName: String): Long? {
        if (!isBackup(fileName)) return null
        return fileName
            .removePrefix(PREFIX)
            .removeSuffix(SUFFIX)
            .toLongOrNull()
    }
}
