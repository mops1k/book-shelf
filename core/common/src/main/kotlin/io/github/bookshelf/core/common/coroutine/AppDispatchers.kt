package io.github.bookshelf.core.common.coroutine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Все диспетчеры приложения в одном месте.
 *
 * Отдельный [metadata] нужен из-за политики проекта: парсинг метаданных идёт с ограниченным
 * параллелизмом (2 на e-ink по умолчанию, настраивается), чтобы не выжигать батарею и не
 * конкурировать с UI за CPU. См. `«Архитектура»` §4.4.
 */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher

    /** Ограниченный параллелизм для парсеров и извлечения обложек. */
    val metadata: CoroutineDispatcher
}
