package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.ReaderRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [ReaderRepository].
 *
 * Что должна делать (Sprint 5, ADR-005):
 * * `installedReaders` — профили из `reader_profiles`, объединённые с найденными в системе
 *   (`PackageManager.queryIntentActivities` в `:core:readerlink`);
 * * `defaultForFormat` / `setDefault` — чтение и запись `format_defaults`;
 * * `openBook` — резолвер (override книги → дефолт формата → последняя → диалог) и запуск через
 *   `:core:readerlink`; `ActivityNotFoundException` превращается в понятный `AppError`, а не падение.
 */
@Singleton
class ReaderRepositoryImpl @Inject constructor() : ReaderRepository {

    override fun installedReaders(): Flow<List<ReaderProfileSummary>> = flowOf(emptyList())

    override fun defaultForFormat(format: String): Flow<String?> = flowOf(null)

    override suspend fun setDefault(format: String, profileId: String?): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 5): ReaderRepository.setDefault"))

    override suspend fun openBook(bookId: String, profileId: String?): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 5): ReaderRepository.openBook"))
}
