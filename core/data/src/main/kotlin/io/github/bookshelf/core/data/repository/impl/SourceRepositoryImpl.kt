package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.repository.SourceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [SourceRepository].
 *
 * Что должна делать (Sprint 2):
 * * `addSafSource` — `takePersistableUriPermission(FLAG_GRANT_READ_URI_PERMISSION)` **до** записи
 *   в `sources`; если разрешение не дано — `AppError.PermissionDenied` (docs/architecture.md §4.1);
 * * `observeSources` — `Flow` по `sources` с `accessLost`/`documentCount`;
 * * `removeSource` — пометить книги `isMissing`, но не удалять (ADR-010);
 * * `rescan` — поставить уникальную работу `ScanSourceWorker` с `ExistingWorkPolicy.KEEP`.
 */
@Singleton
class SourceRepositoryImpl @Inject constructor() : SourceRepository {

    override fun observeSources(): Flow<List<Source>> = flowOf(emptyList())

    override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 2): SourceRepository.addSafSource"))

    override suspend fun removeSource(sourceId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 2): SourceRepository.removeSource"))

    override suspend fun rescan(sourceId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 2): SourceRepository.rescan"))
}
