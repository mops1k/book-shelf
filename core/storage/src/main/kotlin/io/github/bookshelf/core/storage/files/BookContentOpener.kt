package io.github.bookshelf.core.storage.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
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
 * [tempCopy] кладёт файл в `cacheDir/import` с лимитом 100 МБ по умолчанию
 * (docs/architecture.md §4.4).
 */
interface BookContentOpener {

    /** Одноразовый поток чтения с начала файла; `null`, если провайдер его не отдал. */
    fun openStream(documentUri: String): InputStream?

    /** Seekable-канал либо `null`, если источник не seekable (облако/pipe). */
    fun openSeekableChannel(documentUri: String): FileChannel?

    /** Размер в байтах, если провайдер его сообщает; `null`, если колонка `SIZE` пуста. */
    fun sizeOf(documentUri: String): Long?

    /**
     * Временная копия файла в `cacheDir/import`.
     *
     * @return путь к копии; ошибка — если файл больше лимита или запись не удалась.
     */
    fun tempCopy(documentUri: String): AppResult<String>
}

/**
 * Реализация доступа к содержимому через `ContentResolver` (docs/architecture.md §4.4).
 *
 * @param resolver резолвер приложения; время жизни — процесс.
 * @param importDir каталог временных копий `cacheDir/import`.
 * @param maxTempCopyBytes потолок копии: файл в сотни мегабайт из облака не должен съесть память
 *   устройства, поэтому размер проверяется до начала копирования.
 */
class DefaultBookContentOpener(
    private val resolver: ContentResolver,
    private val importDir: File,
    private val maxTempCopyBytes: Long = DEFAULT_MAX_TEMP_COPY_BYTES,
) : BookContentOpener {

    override fun openStream(documentUri: String): InputStream? =
        runCatching { resolver.openInputStream(Uri.parse(documentUri)) }.getOrNull()

    /**
     * Канал с seek — нужен форматам с произвольным доступом (PDF, ZIP, MOBI).
     *
     * Локальные провайдеры отдают настоящий файл, и seek там есть. Облачные (Drive и подобные)
     * возвращают pipe, где `position` бросает исключение, поэтому не-seekable — это `null`,
     * а вызывающий делает временную копию.
     *
     * Дескриптор намеренно НЕ закрывается здесь: `ParcelFileDescriptor` и канал из его
     * `fileDescriptor` — это один и тот же открытый файл. Закрыть PFD в `use` означало бы
     * вернуть вызывающему уже мёртвый канал. Владение дескриптором переходит к вызывающему.
     */
    override fun openSeekableChannel(documentUri: String): FileChannel? = runCatching {
        resolver.openFileDescriptor(Uri.parse(documentUri), "r")
            ?.let { descriptor -> FileInputStream(descriptor.fileDescriptor).channel }
    }.getOrNull()

    /**
     * Размер через колонку `OpenableColumns.SIZE`.
     *
     * Провайдер вправе её не отдавать: у потоков из Google Drive размера нет вовсе. Тогда `null`,
     * чтобы вызывающий решал иначе, а не получил выдуманный ноль.
     */
    override fun sizeOf(documentUri: String): Long? = runCatching {
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
     * Временная копия для не-seekable источников.
     *
     * Сначала проверяется размер: копировать 500 МБ, чтобы упереться в лимит на середине, хуже,
     * чем отказать сразу. Имя уникальное — два скана одного каталога идут параллельно и не должны
     * затирать чужую копию.
     */
    override fun tempCopy(documentUri: String): AppResult<String> {
        val declaredSize = sizeOf(documentUri)
        if (declaredSize != null && declaredSize > maxTempCopyBytes) {
            return AppResult.failure(
                AppError.InvalidArgument(
                    what = "bookSize",
                    message = "Файл слишком большой для временной копии: $declaredSize байт",
                ),
            )
        }

        // Запись идёт во временный файл с последующим переименованием: читатель, который
        // увидит полуготовую копию, попытается её распарсить и упадёт на битом формате.
        val name = UUID.randomUUID().toString()
        val temp = File(importDir, "$name$TEMP_SUFFIX")
        val target = File(importDir, name)
        return runCatching {
            if (!importDir.exists()) importDir.mkdirs()
            val source = resolver.openInputStream(Uri.parse(documentUri))
                ?: error("Провайдер не отдал поток")
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
        /** Лимит временной копии — 100 МБ (docs/architecture.md §4.4). */
        const val DEFAULT_MAX_TEMP_COPY_BYTES: Long = 100L * 1024L * 1024L

        private const val TEMP_SUFFIX = ".tmp"
        private val SIZE_PROJECTION = arrayOf(OpenableColumns.SIZE)
    }
}
