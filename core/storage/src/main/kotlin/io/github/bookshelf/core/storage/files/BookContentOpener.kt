package io.github.bookshelf.core.storage.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.io.SeekableFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.util.UUID

/**
 * Доступ к содержимому книги по `content://`-URI.
 *
 * `ContentResolver.openFileDescriptor(uri, "r")` → `FileInputStream(pfd.fileDescriptor).channel`
 * даёт seek для локальных файлов (внешнее и внутреннее хранилище). Для облачных
 * DocumentsProvider (Google Drive и подобные) это pipe: [openSeekableChannel] вернёт `null`,
 * и форматы с произвольным доступом (MOBI/AZW3, PDF, CBZ/CBR) работают через временную копию —
 * [tempCopy] кладёт файл в `cacheDir/import` с лимитом из настроек группы «Полка»
 * (`library.tempCopyLimitMb`, умолчание 100 МБ, «Архитектура» §4.4).
 */
/**
 * Факты документа, которые скан сравнивает с файлом.
 *
 * @param sizeBytes размер; `null` — провайдер его не сообщает.
 * @param lastModified время изменения в миллисекундах; `null` — провайдер его не сообщает.
 */
data class DocumentFacts(
    val sizeBytes: Long?,
    val lastModified: Long?,
)

interface BookContentOpener {

    /** Одноразовый поток чтения с начала файла; `null`, если провайдер его не отдал. */
    fun openStream(documentUri: String): InputStream?

    /** Seekable-канал либо `null`, если источник не seekable (облако/pipe). */
    fun openSeekableChannel(documentUri: String): SeekableFile?

    /** Размер в байтах, если провайдер его сообщает; `null`, если колонка `SIZE` пуста. */
    fun sizeOf(documentUri: String): Long?

    /**
     * Факты документа: размер и время изменения.
     *
     * Нужны после замены содержимого. Скан сравнивает эти два значения с файлом и по расхождению
     * решает, что файл изменился, — а значит, перечитывает метаданные, сбрасывает обложку и снимает
     * отметку оптимизации. Записав то, что сообщает провайдер, мы не заставляем скан перечитывать
     * книгу, которую только что сами и переписали.
     *
     * `null` — провайдер не ответил; отдельные поля `null`, если он не сообщает их.
     */
    fun factsOf(documentUri: String): DocumentFacts?

    /**
     * Временная копия файла в `cacheDir/import`.
     *
     * @return путь к копии; ошибка — если файл больше лимита или запись не удалась.
     */
    fun tempCopy(documentUri: String): AppResult<String>

    /**
     * Копия книги для чужой программы: файл в каталоге экспорта.
     *
     * Отличается от [tempCopy] местом: временная копия нужна нам (разбор метаданных, чтение
     * облачной книги), а эта — читалке, которой нельзя отдать `content://` от SAF. Каталог экспорта
     * открыт через `FileProvider`, поэтому адрес копии видит другая программа; каталог временных
     * копий наружу не отдаётся.
     *
     * Тот же лимит (`library.tempCopyLimitMb`): это тоже запись на диск, и человек управляет ею
     * одной настройкой.
     *
     * @param extension расширение книги (`epub`, `fb2.zip`). Попадает в имя копии: читалка,
     *   которая определяет формат по имени файла, иначе файл не опознаёт и открывает свою
     *   последнюю книгу вместо выбранной. Значение приходит из имени чужого файла, поэтому
     *   в имя попадают только буквы, цифры и точки (`..` и разделители пути отбрасываются).
     */
    fun exportCopy(documentUri: String, extension: String): AppResult<String>

    /**
     * Записать новое содержимое в **существующий** документ.
     *
     * Нужно оптимизации книги: она заменяет оригинал уменьшенным файлом. Переименование в SAF
     * не поддерживается («Update not supported»), поэтому замена — это запись в тот же документ
     * в режиме усечения. Безопасность обеспечивает вызывающий: он держит исходные байты и
     * возвращает их обратно, если запись не удалась.
     */
    fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit>
}

/**
 * Реализация доступа к содержимому через `ContentResolver` («Архитектура» §4.4).
 *
 * @param resolver резолвер приложения; время жизни — процесс.
 * @param importDir каталог временных копий `cacheDir/import`.
 * @param tempCopyLimitBytes потолок копии в байтах: файл в сотни мегабайт из облака не должен
 *   съесть память устройства, поэтому размер проверяется до начала копирования. Именно функция, а
 *   не число: лимит — это настройка `library.tempCopyLimitMb`, а объект существует один на процесс
 *   (`@Singleton`), поэтому значение читается в момент копирования и изменение настройки
 *   применяется без перезапуска приложения («План работ», DoD Sprint 5). По умолчанию —
 *   прежняя константа [DEFAULT_MAX_TEMP_COPY_BYTES]: без неё объект нельзя собрать в тестах, и
 *   поведение по умолчанию осталось бы без лимита вовсе.
 */
class DefaultBookContentOpener(
    private val resolver: ContentResolver,
    private val importDir: File,
    private val exportDir: File,
    private val tempCopyLimitBytes: () -> Long = { DEFAULT_MAX_TEMP_COPY_BYTES },
) : BookContentOpener {

    override fun openStream(documentUri: String): InputStream? = runCatching {
        val path = filePathOf(documentUri)
        if (path != null) FileInputStream(File(path)) else resolver.openInputStream(Uri.parse(documentUri))
    }.getOrNull()

    /**
     * Канал с seek — нужен форматам с произвольным доступом (PDF, ZIP, MOBI).
     *
     * Локальные провайдеры отдают настоящий файл, и seek там есть. Облачные (Drive и подобные)
     * возвращают pipe, где `position` бросает исключение, поэтому не-seekable — это `null`,
     * а вызывающий делает временную копию.
     *
     * Владение дескриптором переходит к вызывающему вместе с каналом: возвращается [SeekableFile],
     * который закрывает и канал, и `ParcelFileDescriptor`. Держать дескриптор рядом обязательно:
     * его финализатор закрыл бы файл под живым каналом, когда ссылка на дескриптор потеряна
     * (находка аудита 28.09.2026).
     */
    override fun openSeekableChannel(documentUri: String): SeekableFile? = runCatching {
        val path = filePathOf(documentUri)
        if (path != null) {
            // Владелец — сам поток: закрытие канала и потока вместе освобождает дескриптор файла.
            val stream = FileInputStream(File(path))
            SeekableFile(stream.channel, stream)
        } else {
            resolver.openFileDescriptor(Uri.parse(documentUri), "r")?.let { descriptor ->
                SeekableFile(FileInputStream(descriptor.fileDescriptor).channel, descriptor)
            }
        }
    }.getOrNull()

    /**
     * Размер через колонку `OpenableColumns.SIZE`.
     *
     * Провайдер вправе её не отдавать: у потоков из Google Drive размера нет вовсе. Тогда `null`,
     * чтобы вызывающий решал иначе, а не получил выдуманный ноль.
     */
    override fun sizeOf(documentUri: String): Long? = runCatching {
        val path = filePathOf(documentUri)
        if (path != null) {
            val file = File(path)
            return@runCatching if (file.isFile) file.length() else null
        }
        val cursor = resolver.query(Uri.parse(documentUri), SIZE_PROJECTION, null, null, null)
            ?: return@runCatching null
        cursor.use {
            if (!it.moveToFirst()) return@runCatching null
            // Индекс ищем по имени колонки, а не по позиции: провайдер вправе вернуть свои
            // колонки в другом порядке, и жёсткий индекс 0 молча прочитал бы чужое поле.
            val index = it.getColumnIndexOrThrow(OpenableColumns.SIZE)
            if (it.isNull(index)) null else it.getLong(index)
        }
    }.getOrNull()

    /**
     * Размер и время изменения одним запросом.
     *
     * Колонок может не быть вовсе: `LAST_MODIFIED` объявлена у `DocumentsProvider`, и не каждый
     * провайдер её отдаёт. Тогда поле `null`, а вызывающий оставляет прежнее значение — врать
     * выдуманным временем нельзя, скан по нему решит, что файл не менялся.
     */
    override fun factsOf(documentUri: String): DocumentFacts? = runCatching {
        val path = filePathOf(documentUri)
        if (path != null) {
            val file = File(path)
            return@runCatching if (file.isFile) {
                DocumentFacts(
                    sizeBytes = file.length(),
                    lastModified = file.lastModified().takeIf { it > 0L },
                )
            } else {
                null
            }
        }
        val cursor = resolver.query(Uri.parse(documentUri), FACTS_PROJECTION, null, null, null)
            ?: return@runCatching null
        cursor.use {
            if (!it.moveToFirst()) return@runCatching null
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            val modifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            DocumentFacts(
                sizeBytes = if (sizeIndex < 0 || it.isNull(sizeIndex)) null else it.getLong(sizeIndex),
                lastModified = if (modifiedIndex < 0 || it.isNull(modifiedIndex)) {
                    null
                } else {
                    it.getLong(modifiedIndex)
                },
            )
        }
    }.getOrNull()

    /**
     * Временная копия для не-seekable источников.
     *
     * Сначала проверяется размер: копировать 500 МБ, чтобы упереться в лимит на середине, хуже,
     * чем отказать сразу. Имя уникальное — два скана одного каталога идут параллельно и не должны
     * затирать чужую копию. Лимит читается один раз на копию: иначе проверка до записи и проверка
     * после неё могли бы увидеть разные значения, если пользователь поменял настройку между ними.
     */
    override fun tempCopy(documentUri: String): AppResult<String> =
        copyInto(directory = importDir, purpose = "временной копии", documentUri = documentUri)

    override fun exportCopy(documentUri: String, extension: String): AppResult<String> =
        copyInto(
            directory = exportDir,
            purpose = "экспорта",
            documentUri = documentUri,
            extension = extension,
        )

    /**
     * Замена содержимого документа.
     *
     * Режим `"wt"` (усечение) обязателен: без него провайдер дописал бы новое содержимое к старому,
     * и книга превратилась бы в мусор.
     */
    override fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit> = appRunCatching {
        val path = filePathOf(documentUri)
        val output = if (path != null) {
            FileOutputStream(File(path))
        } else {
            resolver.openOutputStream(Uri.parse(documentUri), WRITE_TRUNCATE)
                ?: error("Провайдер не открыл файл для записи: $documentUri")
        }
        output.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }

    /**
     * Общая часть обеих копий: отличаются только каталогом, словом в сообщении об ошибке и
     * расширением в имени файла (оно нужно только копии для читалки).
     */
    private fun copyInto(
        directory: File,
        purpose: String,
        documentUri: String,
        extension: String? = null,
    ): AppResult<String> {
        val maxTempCopyBytes = tempCopyLimitBytes()
        val declaredSize = sizeOf(documentUri)
        if (declaredSize != null && declaredSize > maxTempCopyBytes) {
            return AppResult.failure(
                AppError.InvalidArgument(
                    what = "bookSize",
                    message = "Файл слишком большой для $purpose: $declaredSize байт",
                ),
            )
        }

        // Запись идёт во временный файл с последующим переименованием: читатель, который
        // увидит полуготовую копию, попытается её распарсить и упадёт на битом формате.
        val name = copyName(id = UUID.randomUUID().toString(), extension = extension)
        val temp = File(directory, "$name$TEMP_SUFFIX")
        val target = File(directory, name)
        return runCatching {
            if (!directory.exists()) directory.mkdirs()
            val source = openStream(documentUri)
                ?: error("Источник не отдал поток: $documentUri")
            source.use { input -> FileOutputStream(temp).use { output -> input.copyTo(output) } }
            // Провайдер мог солгать в SIZE или не отдать его вовсе: проверяем по факту.
            if (temp.length() > maxTempCopyBytes) {
                error("Файл больше лимита копии: ${temp.length()} байт")
            }
            if (!temp.renameTo(target)) error("Не удалось переименовать временную копию")
            target.absolutePath
        }.fold(
            onSuccess = { AppResult.success(it) },
            onFailure = {
                temp.delete()
                target.delete()
                AppResult.failure(AppError.Io(message = it.message, cause = it))
            },
        )
    }

    companion object {
        /** Резервный лимит временной копии — 100 МБ («Архитектура» §4.4): совпадает с умолчанием настройки. */
        const val DEFAULT_MAX_TEMP_COPY_BYTES: Long = 100L * 1024L * 1024L

        /** Режим записи с усечением: иначе провайдер допишет новое к старому содержимому. */
        const val WRITE_TRUNCATE = "wt"

        private const val TEMP_SUFFIX = ".tmp"

        /** Сколько частей расширения попадает в имя копии: `fb2`, `fb2.zip`. */
        private const val MAX_EXTENSION_PARTS = 2

        /** Предел длины одной части: настоящее расширение короче, а чужой мусор — длиннее. */
        private const val MAX_EXTENSION_PART = 8

        /**
         * Имя файла копии: уникальный id плюс расширение книги.
         *
         * Расширение приходит из чужого имени файла, поэтому в имя попадают только буквы и цифры,
         * разделённые точками, — и не больше двух последних частей по [MAX_EXTENSION_PART]
         * символов. Так `../fb2/../../secret` превращается в `fb2.secret`, а не в путь наружу,
         * и в имени не остаётся `..`. Пустое расширение не добавляет точку вовсе.
         */
        internal fun copyName(id: String, extension: String?): String {
            val suffix = extension
                ?.lowercase()
                ?.split('.', '/', '\\')
                ?.filter { part -> part.length in 1..MAX_EXTENSION_PART && part.all(Char::isLetterOrDigit) }
                ?.takeLast(MAX_EXTENSION_PARTS)
                ?.joinToString(".")
                .orEmpty()
            return if (suffix.isEmpty()) id else "$id.$suffix"
        }

        private val SIZE_PROJECTION = arrayOf(OpenableColumns.SIZE)

        /** Размер и время изменения: время объявлено у `DocumentsProvider`, а не у `OpenableColumns`. */
        private val FACTS_PROJECTION = arrayOf(
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

/**
 * Путь файла в файловой системе или `null`, если адрес обслуживает провайдер.
 *
 * Источники съёмного носителя на прошивках без публичной точки `/storage/<uuid>` читаются по
 * обычному пути (`/mnt/media_rw/<uuid>/…`, проверено на Bigme B6 Color 02.10.2026), и opener обязан
 * понимать и такие адреса, и `file://`. Схема `content://` остаётся за `ContentResolver`.
 */
internal fun filePathOf(documentUri: String): String? {
    val trimmed = documentUri.trim()
    return when {
        trimmed.isEmpty() -> null
        trimmed.startsWith(CONTENT_SCHEME, ignoreCase = true) -> null
        trimmed.startsWith(FILE_SCHEME, ignoreCase = true) -> trimmed.substring(FILE_SCHEME.length)
        else -> trimmed
    }
}

private const val CONTENT_SCHEME = "content://"
private const val FILE_SCHEME = "file://"
