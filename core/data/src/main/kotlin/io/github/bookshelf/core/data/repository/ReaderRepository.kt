package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import kotlinx.coroutines.flow.Flow

/**
 * Внешние читалки (docs/architecture.md §7, ADR-005).
 *
 * Резолвер выбора профиля (override книги → дефолт формата → последняя использованная → диалог
 * «Открыть в…») живёт в `:core:readerlink`; `:core:data` только хранит выбор пользователя и
 * делегирует запуск. Приложение обязано корректно работать без FBReader и без его API:
 * любые ошибки привязки — тихая деградация до L1.
 */
interface ReaderRepository {

    /** Установленные читалки и пользовательские профили. */
    fun installedReaders(): Flow<List<ReaderProfileSummary>>

    /** Id профиля по умолчанию для формата (`fb2`, `epub`, …) или `null`. */
    fun defaultForFormat(format: String): Flow<String?>

    /** Назначить читалку по умолчанию для формата; `null` — «спрашивать всегда». */
    suspend fun setDefault(format: String, profileId: String?): AppResult<Unit>

    /** Открыть книгу в читалке; [profileId] == null — выбрать по правилам резолвера. */
    suspend fun openBook(bookId: String, profileId: String? = null): AppResult<Unit>
}
