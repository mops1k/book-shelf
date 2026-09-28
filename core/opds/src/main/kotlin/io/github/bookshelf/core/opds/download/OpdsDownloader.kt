package io.github.bookshelf.core.opds.download

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.auth.OpdsCredentials
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

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
    CANCELLED,
}

/**
 * Загрузчик с потоковой записью.
 *
 * Реализация появилась после того, как выяснилось: очередь загрузок накапливалась, а файлы
 * не появлялись — весь загрузчик был заглушкой `TODO`. Поток идёт мимо `ByteArray` целиком,
 * книга на десятки мегабайт в память не втягивается.
 */
@Singleton
class DefaultOpdsDownloader @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val writer: io.github.bookshelf.core.storage.saf.SafTreeWriter,
) : OpdsDownloader {

    /** Каталог промежуточных файлов внутри приватного хранилища приложения. */
    private val stagingDir: java.io.File
        get() = java.io.File(context.cacheDir, STAGING_DIR).apply { mkdirs() }

    // Клиент собирается здесь же, а не берётся из DI: в проекте OkHttp не вынесен в
    // зависимости целиком, `OkHttpOpdsClient` тоже строит свой. Две независимые копии
    // соединений — нормальная цена за отсутствие лишней зависимости.
    private val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    override suspend fun enqueue(request: DownloadRequest): AppResult<String> {
        val id = request.entryId ?: java.util.UUID.randomUUID().toString()
        val queued = DownloadProgress(id, 0, request.expectedSizeBytes, DownloadState.QUEUED)
        progress.value = progress.value + (id to queued)
        return AppResult.success(id)
    }

    override fun observe(downloadId: String): Flow<DownloadProgress> =
        progress.asStateFlow().map { it[downloadId] ?: DownloadProgress(downloadId, 0, null, DownloadState.QUEUED) }

    override suspend fun cancel(downloadId: String) {
        // Сброс состояния: прерванную загрузку должен убрать вызывающий, он же удаляет `.part`.
        progress.value = progress.value + (
            downloadId to DownloadProgress(downloadId, 0, null, DownloadState.CANCELLED)
            )
    }

    /**
     * Скачать книгу в указанное дерево SAF.
     *
     * Пишет сначала в `<имя>.part` и только потом переименовывает: оборванная загрузка не
     * оставляет файла, который следующий скан принял бы за книгу.
     *
     * @return `content://`-URI скачанного файла.
     */
    suspend fun download(
        request: DownloadRequest,
        treeUri: String,
        checkCancelled: () -> Boolean = { false },
        onProgress: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): AppResult<String> {
        val fileName = fileNameOf(request)
        // Схема «сначала `.part` в папке, потом переименование» на SAF не работает: системный
        // провайдер внешнего хранилища не поддерживает переименование через `update`
        // (ошибка «Update not supported»), и загрузка падала уже после успешной передачи байтов.
        // Поэтому качаем в приватный файл приложения, а в дерево SAF переносим одним проходом
        // уже под финальным именем — провайдер при этом не переименовывает ничего.
        val staging = java.io.File(stagingDir, "$fileName$PART_SUFFIX")
        return try {
            val call = client.newCall(requestBuilder(request, request.url))
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return AppResult.failure(
                        io.github.bookshelf.core.common.error.AppError.Network(
                            httpCode = response.code,
                            message = "Сервер ответил кодом ${response.code}",
                        ),
                    )
                }
                val body = response.body ?: return AppResult.failure(
                    io.github.bookshelf.core.common.error.AppError.Network(message = "Пустой ответ сервера"),
                )
                val total = body.contentLength().takeIf { size -> size > 0 } ?: -1L
                var written = 0L
                staging.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (checkCancelled()) throw java.io.InterruptedIOException("Загрузка отменена")
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                    output.flush()
                }
            }

            // Перенос в дерево: файл появляется у пользователя целиком и сразу под своим именем.
            writer.writeStream(
                treeUri = treeUri,
                relativeDir = request.targetRelativeDir.orEmpty(),
                fileName = fileName,
                // Настоящий MIME, а не `application/octet-stream`: провайдер внешнего хранилища
                // отказывает в создании документа с безымянным типом («Update not supported»).
                mimeType = request.mimeType?.takeIf { it.isNotBlank() } ?: DEFAULT_FILE_MIME,
            ) { emit ->
                staging.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (checkCancelled()) throw java.io.InterruptedIOException("Загрузка отменена")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        emit(buffer.copyOf(read))
                    }
                }
            }
        } catch (cancelled: java.io.InterruptedIOException) {
            // Причина сохраняется в ошибке: по ней видно, что загрузку оборвали, а не сеть подвела.
            AppResult.failure(
                io.github.bookshelf.core.common.error.AppError.Unknown(
                    message = "Загрузка отменена",
                    cause = cancelled,
                ),
            )
        } catch (error: Throwable) {
            AppResult.failure(
                io.github.bookshelf.core.common.error.AppError.Io(message = error.message ?: "Ошибка загрузки"),
            )
        } finally {
            // Промежуточный файл не переживает загрузку: в кэше он просто копится.
            staging.delete()
        }
    }

    private fun requestBuilder(request: DownloadRequest, url: String): okhttp3.Request =
        okhttp3.Request.Builder()
            .url(url)
            .apply {
                request.credentials?.let { credentials ->
                    header(
                        "Authorization",
                        okhttp3.Credentials.basic(credentials.username, credentials.password),
                    )
                }
            }
            .build()

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val TIMEOUT_SECONDS = 60L
        const val STAGING_DIR = "opds-staging"
        const val DEFAULT_FILE_MIME = "application/octet-stream"
        const val PART_SUFFIX = ".part"
    }
}

/**
 * Имя файла из названия книги.
 *
 * Раньше файл получал имя загрузки (идентификатор), и в папке лежали нечитаемые файлы вроде
 * `3f2a9c1e-....fb2`. Имя строится из названия и расширения, которое угадывается по ссылке.
 */
fun fileNameOf(request: DownloadRequest): String {
    val base = sanitizeFileName(request.title).ifBlank { "book" }
    return "$base.${bookExtensionOf(request)}"
}

/**
 * Расширение книги для таблицы `books`.
 *
 * То же значение, что в имени файла, и намеренно составное (`fb2.zip`): разбор выбирает
 * парсер по расширению, и обычный `zip` уводил книгу не в тот парсер — разбор падал.
 */
fun bookExtensionOf(request: DownloadRequest): String = extensionOf(request.mimeType, request.url)

/**
 * Расширение файла.
 *
 * У Флибусты ссылки на книги выглядят как `/b/890648/fb2` — расширения в пути нет вовсе,
 * а формат задан только типом `application/fb2+zip`. Плюс в типе означает zip-контейнер,
 * поэтому такой тип даёт имя `.fb2.zip` — именно такое составное расширение знает
 * `MimeTypes`. Иначе выходило нечитаемое `fb2+zip`, и разбор файла падал.
 */
private fun extensionOf(mimeType: String?, url: String): String {
    val fromUrl = url.substringAfterLast('/').substringBefore('?')
        .takeIf { it.contains('.') }
        ?.let { name -> EXTENSION_IN_NAME.find(name)?.value }
    if (!fromUrl.isNullOrBlank()) return fromUrl.trimStart('.').lowercase()

    val subtype = mimeType?.substringAfterLast('/')?.substringBefore(';')?.trim()
        ?: return DEFAULT_EXTENSION
    if (subtype.contains('+')) {
        val container = subtype.substringAfter('+').lowercase()
        val base = subtype.substringBefore('+').lowercase()
        if (base.isNotBlank()) return "$base.$container"
    }
    return if (subtype == "octet-stream") DEFAULT_EXTENSION else subtype.lowercase()
}

/**
 * Очистка названия для имени файла.
 *
 * Запрещены только разделители пути и управляющие символы — они ломают путь. Всё остальное
 * сохраняется: кириллица, «№», кавычки и тире у названий книг встречаются постоянно, а
 * прежняя жёсткая фильтрация превращала «Полдень XXI век 2002 №3» в «Полдень XXI век 2002 _3».
 */
private fun sanitizeFileName(value: String): String = value
    .filterNot { char -> char.isISOControl() || char in FORBIDDEN_IN_NAME }
    .trim()
    .take(MAX_NAME_LENGTH)

private val FORBIDDEN_IN_NAME = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

/** Расширение в имени файла, в том числе составное: `fb2.zip`, `cbr`. */
private val EXTENSION_IN_NAME = Regex("\\.[A-Za-z0-9]+(\\.[A-Za-z0-9]+)?$")

private const val DEFAULT_EXTENSION = "fb2"
private const val MAX_NAME_LENGTH = 120
