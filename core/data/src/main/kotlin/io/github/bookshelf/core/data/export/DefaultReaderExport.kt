package io.github.bookshelf.core.data.export

import android.net.Uri
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.di.ExportDir
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.readerlink.launch.ExportedUriFactory
import io.github.bookshelf.core.readerlink.launch.ReaderExport
import io.github.bookshelf.core.storage.files.BookContentOpener
import java.io.File
import javax.inject.Inject

/**
 * Копия книги для читалки, которой нельзя отдать `content://` от SAF.
 *
 * Три обязанности, и все три обязательны:
 * 1. **Разрешение.** Без включённого временного экспорта копия не делается: это запись на диск
 *    размером с книгу, и разрешать её без спроса нельзя.
 * 2. **Копия** — в каталог, открытый через FileProvider (иначе другая программа копию не увидит),
 *    с тем же лимитом, что и временная копия для нашего чтения.
 * 3. **Уборка.** Копии книг бывают на десятки мегабайт, а каталог экспорта никто, кроме нас, не
 *    чистит: старые удаляются перед новой копией. Каталог лежит в `cacheDir`, поэтому система
 *    может очистить его и целиком — на этот случай копия всегда создаётся заново.
 */
class DefaultReaderExport @Inject constructor(
    private val opener: BookContentOpener,
    private val settings: SettingsRepository,
    private val uriFactory: ExportedUriFactory,
    @ExportDir private val exportDir: File,
    private val clock: AppClock,
) : ReaderExport {

    override suspend fun exportedReaderUri(documentUri: String): AppResult<Uri> {
        if (!settings.current().reader.tempExportEnabled) {
            return AppResult.failure(
                AppError.PermissionDenied(
                    message = "Временный экспорт выключен — включите его в настройках → «Читалки».",
                ),
            )
        }
        pruneOldExports()
        val copy = opener.exportCopy(documentUri)
        val path = copy.getOrNull()
            ?: return AppResult.failure(
                copy.errorOrNull() ?: AppError.Unknown(message = "Копия книги не подготовлена"),
            )
        return AppResult.success(uriFactory.uriFor(File(path)))
    }

    /**
     * Удалить копии, к которым читалка уже не вернётся.
     *
     * Срок — [EXPORT_TTL_MILLIS]. Убираем только файлы: каталог принадлежит нам, и ничего, кроме
     * копий, в нём не лежит. Ошибки удаления не прерывают экспорт: не убрали старьё — не повод
     * не открыть книгу.
     */
    private fun pruneOldExports() {
        val deadline = clock.nowMillis() - EXPORT_TTL_MILLIS
        exportDir.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.lastModified() < deadline) file.delete()
        }
    }

    private companion object {
        /** Сутки: за это время читалка либо открыла копию, либо уже не откроет. */
        const val EXPORT_TTL_MILLIS = 24L * 60L * 60L * 1000L
    }
}
