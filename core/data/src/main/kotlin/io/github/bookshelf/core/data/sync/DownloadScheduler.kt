package io.github.bookshelf.core.data.sync

/**
 * Запуск скачивания книги из каталога OPDS.
 *
 * Порт существует, чтобы `:core:data` не зависел от WorkManager: запись в очередь —
 * доменное решение, а способ выполнения — дело платформы («Архитектура» §7).
 */
interface DownloadScheduler {

    /**
     * Поставить работу на скачивание.
     *
     * @param downloadId строка таблицы `opds_downloads`, из которой работа берёт всё остальное.
     */
    fun schedule(downloadId: String)
}
