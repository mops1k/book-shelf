package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.OpdsCatalog
import io.github.bookshelf.core.data.model.OpdsDownload
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.repository.OpdsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [OpdsRepository].
 *
 * Что должна делать (Sprint 8, ADR-006, docs/opds.md): CRUD `opds_catalogs`, `browse` через
 * `:core:opds` (conditional GET, кэш фидов в `opds_feed_cache`), постановка `DownloadWorker`
 * с `.part`-файлом и атомарным переименованием, дедупликация по `dc:identifier`. Пароль каталога
 * хранится только в шифрованном хранилище, в открытом виде не логируется.
 */
@Singleton
class OpdsRepositoryImpl @Inject constructor() : OpdsRepository {

    override fun observeCatalogs(): Flow<List<OpdsCatalog>> = flowOf(emptyList())

    override suspend fun addCatalog(
        title: String,
        url: String,
        username: String?,
        password: String?,
    ): AppResult<OpdsCatalog> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 8): OpdsRepository.addCatalog"))

    override suspend fun updateCatalog(catalog: OpdsCatalog): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 8): OpdsRepository.updateCatalog"))

    override suspend fun removeCatalog(catalogId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 8): OpdsRepository.removeCatalog"))

    override suspend fun browse(catalogId: String, url: String?): AppResult<List<OpdsEntry>> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 8): OpdsRepository.browse"))

    override fun observeDownloads(): Flow<List<OpdsDownload>> = flowOf(emptyList())

    override suspend fun enqueueDownload(
        entry: OpdsEntry,
        catalogId: String,
        targetSourceId: String?,
        targetRelativeDir: String?,
    ): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 8): OpdsRepository.enqueueDownload"))

    override suspend fun cancelDownload(downloadId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 8): OpdsRepository.cancelDownload"))
}
