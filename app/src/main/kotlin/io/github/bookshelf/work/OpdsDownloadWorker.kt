package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.OpdsDownloadDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.model.OpdsDownloadState
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.opds.download.DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE
import io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES
import io.github.bookshelf.core.opds.download.DefaultOpdsDownloader
import io.github.bookshelf.core.opds.download.DownloadRequest
import io.github.bookshelf.core.opds.download.bookExtensionOf
import io.github.bookshelf.core.opds.download.fileNameOf
import io.github.bookshelf.core.opds.download.relativePathOf
import java.util.UUID
import javax.inject.Inject

/**
 * Скачивание книги из OPDS-каталога.
 *
 * Раньше загрузка доходила только до строки в очереди: сам загрузчик был заглушкой
 * `TODO("STUB(Sprint 8)")`, а писателя в дерево SAF в проекте не было вовсе. Обе дырки
 * закрыты; теперь файл действительно появляется в выбранной папке.
 *
 * Ход работы: запись загрузки → дерево SAF источника → потоковая запись в `<имя>.part` →
 * переименование → строка в таблице `books` → обычный разбор метаданных.
 *
 * Ошибка загрузки — это состояние записи, а не падение работы: следующие книги в очереди
 * не должны вставать из-за одной плохой ссылки.
 */
@HiltWorker
class OpdsDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: OpdsDownloadDao,
    private val bookDao: BookDao,
    private val sourceDao: SourceDao,
    private val downloader: DefaultOpdsDownloader,
    private val metadataScheduler: io.github.bookshelf.core.data.sync.MetadataScheduler,
    private val clock: AppClock,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val download = downloadDao.getById(downloadId) ?: return Result.success()
        val source = sourceDao.getById(download.targetSourceId)
            ?: return fail(downloadId, "Папка-получатель больше не существует")

        updateState(downloadId, OpdsDownloadState.RUNNING, bytes = 0L, error = null)

        val template = fileNameTemplate()
        val request = requestFrom(download, template)

        val result = downloader.download(
            request = request,
            treeUri = source.uri,
            checkCancelled = { isStopped },
            onProgress = { downloaded, _ ->
                // Прогресс пишем не чаще раза в полсекунды: обновление строки на каждый
                // блок забивает базу, а на e-ink экран всё равно не перерисовывается чаще.
                val now = clock.nowMillis()
                if (now - lastProgressAt > PROGRESS_INTERVAL_MS) {
                    lastProgressAt = now
                    updateState(downloadId, OpdsDownloadState.RUNNING, bytes = downloaded, error = null)
                }
            },
        )

        // Без `fold`: его лямбды не suspend, а запись в базу внутри них suspend-функция.
        val documentUri = result.getOrNull()
            ?: return fail(downloadId, downloadErrorMessage(result.errorOrNull()?.message))
        val bookId = "doc:${sha256(documentUri)}"
        bookDao.upsertAll(listOf(newBookEntity(bookId, documentUri, download, source.id, template)))
        downloadDao.upsert(download.copy(state = OpdsDownloadState.DONE, bookId = bookId, error = null))
        metadataScheduler.enqueueMetadata(bookId)
        return Result.success()
    }

    private suspend fun fail(downloadId: String, message: String): Result {
        updateState(downloadId, OpdsDownloadState.FAILED, bytes = 0L, error = message)
        return Result.success()
    }

    private suspend fun updateState(
        downloadId: String,
        state: OpdsDownloadState,
        bytes: Long,
        error: String?,
    ) {
        val current = downloadDao.getById(downloadId) ?: return
        downloadDao.upsert(current.copy(state = state, bytesDownloaded = bytes, error = error))
    }

    private var lastProgressAt: Long = 0L

    /**
     * Шаблон имени файла из настроек (`opds.downloadFileNameTemplate`).
     *
     * Незнакомое значение отбрасывается в пользу документированного умолчания: настройку могла
     * оставить прежняя версия приложения, и имя файла из «чужого» шаблона лучше не собирать —
     * иначе книга получит имя, которого никто не ожидает, а разбор пойдёт по расширению.
     */
    private suspend fun fileNameTemplate(): String {
        val stored = runCatching { settingsRepository.current().opds.downloadFileNameTemplate }
            .getOrNull()
        return stored?.takeIf { it in DOWNLOAD_FILE_NAME_TEMPLATES }
            ?: DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE
    }

    /**
     * Запрос загрузки по строке очереди.
     *
     * Один сборщик на два места — саму загрузку и строку библиотеки: раньше во втором авторы были
     * пустым списком, и с шаблоном имени файла на диске оказалось бы имя с автором, а в базе —
     * без него, то есть открытие книги шло бы по чужому пути.
     */
    private fun requestFrom(
        download: io.github.bookshelf.core.database.entity.OpdsDownloadEntity,
        template: String,
    ): DownloadRequest = DownloadRequest(
        catalogId = download.catalogId,
        entryId = download.entryId,
        title = download.title,
        authors = download.authorsText
            ?.split(',')
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        url = download.downloadUrl,
        mimeType = download.mimeType,
        expectedSizeBytes = download.expectedSize,
        targetSourceId = download.targetSourceId,
        targetRelativeDir = download.targetRelativeDir,
        credentials = null,
        series = download.seriesName,
        fileNameTemplate = template,
    )

    /**
     * Строка библиотеки для скачанного файла.
     *
     * Метаданные оставляем пустыми: их разберёт `MetadataWorker` по той же схеме, что и
     * отсканированные книги, — иначе форматы FB2/EPUB/PDF читались бы дважды и по-разному.
     */
    private fun newBookEntity(
        bookId: String,
        documentUri: String,
        download: io.github.bookshelf.core.database.entity.OpdsDownloadEntity,
        sourceId: String,
        template: String,
    ): io.github.bookshelf.core.database.entity.BookEntity {
        val now = clock.nowMillis()
        val request = requestFrom(download, template)
        val fileName = fileNameOf(request)
        return io.github.bookshelf.core.database.entity.BookEntity(
            id = bookId,
            sourceId = sourceId,
            documentUri = documentUri,
            // Путь с папками из шаблона: файл лежит в подпапках, и карточка книги обязана
            // показать тот же путь, иначе «Путь» в ней разойдётся с диском.
            relativePath = relativePathOf(request),
            fileName = fileName,
            // Составное расширение (`fb2.zip`), а не хвост после последней точки:
            // по `zip` разбор уводил книгу не в тот парсер.
            extension = bookExtensionOf(request),
            mimeType = download.mimeType.orEmpty(),
            sizeBytes = download.expectedSize ?: 0L,
            lastModified = now,
            // Название из фида — лучше, чем имя файла: в нём нет подстановок «_».
            title = download.title.ifBlank { fileName },
            sortTitle = download.title.ifBlank { fileName },
            authorsText = download.authorsText.orEmpty(),
            addedAt = now,
            updatedAt = now,
            lastSeenAt = now,
        )
    }

    companion object {
        /** Ключ входных данных: идентификатор загрузки. */
        const val KEY_DOWNLOAD_ID: String = "downloadId"

        private const val PROGRESS_INTERVAL_MS = 500L

        private fun sha256(value: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            return digest.digest(value.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
