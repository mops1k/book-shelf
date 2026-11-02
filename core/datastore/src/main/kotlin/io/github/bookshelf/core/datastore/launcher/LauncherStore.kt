package io.github.bookshelf.core.datastore.launcher

import io.github.bookshelf.core.datastore.model.LauncherState
import kotlinx.coroutines.flow.Flow

/**
 * Состояние лаунчера: избранное, скрытые приложения, сортировка и счётчики запусков (ADR-015).
 *
 * Отдельный интерфейс, а не прямое чтение DataStore из UI: экран приложений и настройки
 * проверяются с подменой хранилища, без файлов и Android-контекста.
 */
interface LauncherStore {

    /** Текущее состояние; изменения приходят без перезапуска приложения. */
    val state: Flow<LauncherState>

    /**
     * Изменить состояние одной транзакцией.
     *
     * `transform` получает актуальное значение внутри транзакции — так параллельные изменения
     * (запуск приложения и переключение сортировки) не теряются.
     */
    suspend fun update(transform: (LauncherState) -> LauncherState)

    /** Однократное чтение — для мест, где поток не нужен. */
    suspend fun current(): LauncherState
}
