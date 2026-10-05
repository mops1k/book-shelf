package io.github.bookshelf.core.readerlink.koreader

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressFileReader
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Прогресс книги из sidecar-файла KOReader.
 *
 * KOReader — единственная из проверенных читалок, чей прогресс лежит **рядом с книгой**, то есть
 * внутри папки библиотеки, которую пользователь уже открыл нашему приложению: поэтому источник
 * работает во всех flavor'ах, включая `play` (там нет доступа ко всей памяти). Проверено на
 * устройстве 05.10.2026.
 *
 * Источник файловый: он не знает, что открыто в читалке сейчас (KOReader наружу этого не отдаёт),
 * и потому [openedBook] возвращает `null` — книгу называет читательская сессия или фоновый проход.
 * Врать здесь нельзя: если бы источник «сказал», что книга открыта, сценарий записал бы чужой
 * прогресс.
 */
@Singleton
class KoReaderProgressSource @Inject constructor(
    private val files: ProgressFileReader,
) : ReadingProgressSource {

    override val id: ProgressSourceId = ProgressSourceId.KOREADER_SIDECAR

    /** KOReader наружу «что открыто» не отдаёт: его читают по конкретной книге. */
    override val knowsOpenBook: Boolean = false

    /** Подключаться не к чему: файлы на карте доступны, пока есть право их читать. */
    override suspend fun connect(): Boolean = true

    override suspend fun disconnect() = Unit

    override suspend fun openedBook(): ReaderBookAddress? = null

    /**
     * Прогресс книги из её sidecar-файла.
     *
     * `Failure`, если файла нет или в нём нет доли прочитанного: отдать ноль процентов значило бы
     * показать «книга не начата» вместо «KOReader эту книгу не открывал».
     */
    override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
        val snapshot = appRunCatching { snapshotFor(book) }.getOrNull()
        return if (snapshot != null) {
            AppResult.success(snapshot)
        } else {
            AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения KOReader",
                    message = "нет sidecar-файла книги ${book.fileName}",
                ),
            )
        }
    }

    private suspend fun snapshotFor(book: ProgressBookRef): ReadingProgressSnapshot? {
        val sibling = KoReaderSidecar.siblingOf(book.fileName)
        val text = files.readNearBook(book, sibling) ?: return null
        val parsed = KoReaderMetadata.parse(text) ?: return null
        return ReadingProgressSnapshot(
            percent = parsed.percentFinished * PERCENT,
            pageIndex = null,
            locatorJson = parsed.locator,
        )
    }

    private companion object {
        const val PERCENT = 100f
    }
}
