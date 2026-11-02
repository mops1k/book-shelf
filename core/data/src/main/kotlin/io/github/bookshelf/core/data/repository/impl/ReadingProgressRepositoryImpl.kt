package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.data.model.ReadingProgress
import io.github.bookshelf.core.data.repository.ReadingProgressRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [ReadingProgressRepository].
 *
 * Что должна делать (Sprint 5/7): читать `reading_progress` (`bookId`, `profileId`, `percent`,
 * `locatorJson`, `source`). Ручных отметок и сброса в контракте больше нет — решение
 * Александра 02.11.2026: ручной прогресс не делаем, прогресс приходит только от читалки
 * (API уровня L2 или собственные источники).
 */
@Singleton
class ReadingProgressRepositoryImpl @Inject constructor() : ReadingProgressRepository {

    override fun observe(bookId: String): Flow<ReadingProgress?> = flowOf(null)
}
