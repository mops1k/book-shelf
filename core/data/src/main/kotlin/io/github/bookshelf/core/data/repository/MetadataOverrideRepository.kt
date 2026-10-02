package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.MetadataOverride
import kotlinx.coroutines.flow.Flow

/**
 * Ручные переопределения метаданных («Архитектура» §3.1, §4.4).
 *
 * Хранятся отдельно от `books`, потому что пересканирование может изменить файл, но **не должно
 * затирать правки пользователя**. Приоритет при слиянии: override → встроенные метаданные → имя
 * файла; флаг «перезаписывать при изменении» — настройка `keepMetadataOnFileChange`.
 */
interface MetadataOverrideRepository {

    fun observe(bookId: String): Flow<MetadataOverride?>

    /** Сохранить (или заменить) переопределение. */
    suspend fun save(override: MetadataOverride): AppResult<Unit>

    /** Убрать переопределение — книга снова следует метаданным файла. */
    suspend fun clear(bookId: String): AppResult<Unit>
}
