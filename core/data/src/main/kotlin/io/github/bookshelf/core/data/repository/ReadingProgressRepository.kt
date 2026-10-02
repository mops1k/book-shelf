package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Прогресс чтения («Архитектура» §7, ADR-005).
 *
 * Прогресс может приходить из API читалки (L2/L3, если он доступен) или ставиться вручную.
 * При отсутствии FBReader/API функция просто не заполняется — это штатное состояние, а не ошибка.
 */
interface ReadingProgressRepository {

    fun observe(bookId: String): Flow<ReadingProgress?>

    /** Ручная отметка прогресса 0..100. */
    suspend fun setManualPercent(bookId: String, percent: Float): AppResult<Unit>

    /** Сбросить прогресс (например, при перечитывании). */
    suspend fun clear(bookId: String): AppResult<Unit>
}
