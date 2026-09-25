package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ReadingProgress
import io.github.bookshelf.core.data.repository.ReadingProgressRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [ReadingProgressRepository].
 *
 * Что должна делать (Sprint 5/7): читать и писать `reading_progress` (`bookId`, `profileId`,
 * `percent`, `locatorJson`, `source`). Записи из API читалки и ручные отметки различаются полем
 * `source`, чтобы синхронизация не затирала ручной прогресс.
 */
@Singleton
class ReadingProgressRepositoryImpl @Inject constructor() : ReadingProgressRepository {

    override fun observe(bookId: String): Flow<ReadingProgress?> = flowOf(null)

    override suspend fun setManualPercent(bookId: String, percent: Float): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): ReadingProgressRepository.setManualPercent"))

    override suspend fun clear(bookId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): ReadingProgressRepository.clear"))
}
