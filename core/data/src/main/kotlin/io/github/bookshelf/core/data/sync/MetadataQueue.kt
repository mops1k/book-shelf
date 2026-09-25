package io.github.bookshelf.core.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * Очередь извлечения метаданных (docs/architecture.md §4.4).
 *
 * Требования, которые определяют контракт:
 * * **приоритет видимых книг.** UI сообщает диапазон видимых индексов через [hint]; книги из этого
 *   диапазона обрабатываются раньше остальных — пользователь видит результат на текущем экране,
 *   а не через полчаса скана;
 * * **ограниченный параллелизм 2** (настройка `library.metadataParallelism`, по умолчанию 2 — на
 *   e-ink берегём батарею и не мешаем UI);
 * * очередь переживает перезапуск процесса: задания хранятся в `parseState = PENDING`, а не в
 *   памяти.
 */
interface MetadataQueue {

    /** Поставить книги в очередь с указанным приоритетом (повторная постановка не дублирует). */
    suspend fun enqueue(bookIds: List<String>, priority: Priority)

    /** Сообщить видимый диапазон индексов полки — приоритетный «быстрый путь». */
    fun hint(visibleRange: IntRange)

    /** Наблюдать состояние очереди для строки прогресса в UI. */
    fun observe(): Flow<MetadataQueueState>

    /** Приоритет обработки: видимые на экране книги обрабатываются первыми. */
    enum class Priority { VISIBLE, BACKGROUND }
}

/**
 * Состояние очереди метаданных.
 *
 * @param queued сколько книг ещё ждёт обработки;
 * @param processed сколько обработано в текущей сессии;
 * @param total сколько всего было поставлено;
 * @param running идёт ли обработка прямо сейчас.
 */
data class MetadataQueueState(
    val queued: Int = 0,
    val processed: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
)
