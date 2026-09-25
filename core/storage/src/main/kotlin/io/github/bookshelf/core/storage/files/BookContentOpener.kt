package io.github.bookshelf.core.storage.files

import io.github.bookshelf.core.common.error.AppResult
import java.io.InputStream
import java.nio.channels.FileChannel

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
 * Реализация поверх `ContentResolver`; появляется в Sprint 3 вместе с первыми парсерами.
 * Сейчас — только контракт, чтобы парсеры и репозиторий писались против интерфейса.
 */
class DefaultBookContentOpener : BookContentOpener {

    override fun openStream(documentUri: String): InputStream? {
        // STUB(Sprint 3): contentResolver.openInputStream(Uri.parse(documentUri)).
        TODO("STUB(Sprint 3): открыть поток через ContentResolver и обернуть ошибки в AppError")
    }

    override fun openSeekableChannel(documentUri: String): FileChannel? {
        // STUB(Sprint 3): openFileDescriptor → FileInputStream(fd).channel; null для pipe.
        TODO("STUB(Sprint 3): FileChannel из дескриптора, null если канал не seekable")
    }

    override fun sizeOf(documentUri: String): Long? {
        // STUB(Sprint 3): размер из OpenableColumns.SIZE или AssetFileDescriptor.length.
        TODO("STUB(Sprint 3): размер через ContentResolver.query(OpenableColumns.SIZE)")
    }

    override fun tempCopy(documentUri: String): AppResult<String> {
        // STUB(Sprint 3): копия в cacheDir/import с лимитом 100 МБ, запись .tmp + rename.
        TODO("STUB(Sprint 3): временная копия для не-seekable источников с лимитом размера")
    }
}
