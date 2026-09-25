package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.MetadataOverride
import io.github.bookshelf.core.data.repository.MetadataOverrideRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [MetadataOverrideRepository].
 *
 * Что должна делать (Sprint 7): хранить правки пользователя отдельно от `books`, чтобы
 * пересканирование изменённого файла не затирало их (при `keepMetadataOnFileChange = false` —
 * наоборот, применять свежие метаданные). Слияние выполняет `:core:data`, а не UI.
 */
@Singleton
class MetadataOverrideRepositoryImpl @Inject constructor() : MetadataOverrideRepository {

    override fun observe(bookId: String): Flow<MetadataOverride?> = flowOf(null)

    override suspend fun save(override: MetadataOverride): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): MetadataOverrideRepository.save"))

    override suspend fun clear(bookId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): MetadataOverrideRepository.clear"))
}
