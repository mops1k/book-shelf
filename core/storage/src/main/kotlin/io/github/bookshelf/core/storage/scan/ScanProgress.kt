package io.github.bookshelf.core.storage.scan

/**
 * Прогресс сканирования источника — то, что видит пользователь на экране скана.
 *
 * Счётчики соответствуют полям `scan_runs` и обновляются батчами, а не на каждый файл:
 * на больших библиотеках (10 000+ файлов) запись в БД и UI на каждый файл съедает бюджет
 * времени (docs/architecture.md §4.2).
 *
 * @param currentPath относительный путь текущей папки для строки прогресса;
 * @param done `true`, когда обход завершён и можно показывать отчёт.
 */
data class ScanProgress(
    val sourceId: String,
    val filesSeen: Long,
    val added: Long,
    val updated: Long,
    val removed: Long,
    val failed: Long,
    val currentPath: String? = null,
    val done: Boolean = false,
)
