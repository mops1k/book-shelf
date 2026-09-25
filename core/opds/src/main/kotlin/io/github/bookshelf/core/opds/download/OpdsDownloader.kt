package io.github.bookshelf.core.opds.download

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.auth.OpdsCredentials
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Загрузка книг из OPDS в папку библиотеки.
 *
 * Требования к реализации (docs/opds.md §4.2, §6):
 * * запись идёт потоково в **`.part`-файл**, затем выполняется **атомарное переименование** —
 *   оборванная загрузка не оставляет мусора, а `.part` удаляется;
 * * докачка по `Range`, если сервер её поддерживает; повтор с backoff при сетевых сбоях;
 * * контроль размера (`Content-Length` + фактически записанные байты), опционально SHA-256 из фида;
 * * имя файла по шаблону с очисткой запрещённых символов; коллизии — суффикс ` (2)`;
 * * прогресс обновляется не чаще раза в секунду (берегём e-ink);
 * * выполняется в `WorkManager` (уникальная работа на `downloadId`), UI не блокируется;
 * * по завершении файл попадает в библиотеку и проходит обычный пайплайн метаданных.
 */
interface OpdsDownloader {

    /** Поставить загрузку в очередь; возвращает `downloadId` для наблюдения и отмены. */
    suspend fun enqueue(request: DownloadRequest): AppResult<String>

    /** Поток прогресса конкретной загрузки. */
    fun observe(downloadId: String): Flow<DownloadProgress>

    /** Отменить загрузку: работа останавливается, `.part` удаляется. */
    suspend fun cancel(downloadId: String)
}

/**
 * Задача на скачивание (соответствует строке `opds_downloads`, docs/opds.md §2).
 *
 * @param catalogId каталог-источник.
 * @param entryId `atom:id`/`dc:identifier` записи; null, если каталог его не дал.
 * @param title название для имени файла и карточки.
 * @param authors авторы для шаблона имени.
 * @param url прямой URL файла (ссылка приобретения, не `rel="alternate"`).
 * @param mimeType MIME из ссылки; null, если каталог его не указал.
 * @param expectedSizeBytes ожидаемый размер; null, если `Content-Length` неизвестен.
 * @param targetSourceId `source` библиотеки, в который качаем; null — папка по умолчанию.
 * @param targetRelativeDir относительная папка внутри источника; null — корень источника.
 * @param credentials учётные данные каталога; null — каталог без авторизации.
 */
data class DownloadRequest(
    val catalogId: String,
    val entryId: String?,
    val title: String,
    val authors: List<String>,
    val url: String,
    val mimeType: String?,
    val expectedSizeBytes: Long?,
    val targetSourceId: String?,
    val targetRelativeDir: String?,
    val credentials: OpdsCredentials?,
)

/**
 * Прогресс загрузки.
 *
 * @param downloadId идентификатор, выданный [OpdsDownloader.enqueue].
 * @param bytesDownloaded записано байт.
 * @param totalBytes ожидаемый полный размер; null, если сервер его не сообщил.
 * @param state текущее состояние.
 */
data class DownloadProgress(
    val downloadId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val state: DownloadState,
)

/** Состояния загрузки — значения колонки `opds_downloads.state` (docs/opds.md §2). */
enum class DownloadState {
    /** В очереди. */
    QUEUED,

    /** Выполняется. */
    RUNNING,

    /** Завершена, файл переименован и добавлен в библиотеку. */
    DONE,

    /** Упала; причина в записи, доступен «Повторить». */
    FAILED,

    /** Отменена пользователем; `.part` удалён. */
    CANCELED,
}

/** Заглушка загрузчика. */
@Singleton
class DefaultOpdsDownloader @Inject constructor() : OpdsDownloader {

    // STUB(Sprint 8): WorkManager unique work на downloadId + потоковая запись в .part

    override suspend fun enqueue(request: DownloadRequest): AppResult<String> =
        TODO("STUB(Sprint 8): поставить загрузку в WorkManager и вернуть downloadId")

    // STUB(Sprint 8): поток прогресса из WorkInfo/байтового счётчика

    override fun observe(downloadId: String): Flow<DownloadProgress> =
        TODO("STUB(Sprint 8): наблюдать прогресс загрузки")

    // STUB(Sprint 8): отмена работы и удаление .part

    override suspend fun cancel(downloadId: String) {
        TODO("STUB(Sprint 8): отменить работу и удалить .part")
    }
}
