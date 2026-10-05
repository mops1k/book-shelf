package io.github.bookshelf.core.data.reader

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.common.log.debug
import io.github.bookshelf.core.readerlink.progress.ProgressStorage
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файлы читалок на общей памяти, доступные по реальному пути.
 *
 * Нужно для Librera: её прогресс лежит в `/sdcard/Librera/profile.Librera/device.<модель>/app-Progress.json`,
 * а каталог зависит от модели устройства — путь не выводится из книги, поэтому каталог сначала
 * смотрим, потом читаем файл по имени. Как и для чужих баз, чужой файл читается **с ограничением
 * объёма**: это файл другой программы, и он может оказаться любого размера.
 *
 * Отсутствие каталога, отсутствие файла и отсутствие доступа ко всей памяти (flavor `play`) — это
 * `null`/пусто, то есть «прогресса нет»; исключения наружу не выходят, иначе одна чужая папка
 * ломала бы всю синхронизацию.
 */
@Singleton
class SharedStorageFiles @Inject constructor(
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : ProgressStorage {

    override suspend fun read(path: String): String? = withContext(dispatchers.io) {
        val file = File(path)
        if (!file.isFile) return@withContext null
        try {
            file.inputStream().use { stream ->
                val bytes = ByteArray(MAX_TEXT_BYTES)
                var total = 0
                while (total < bytes.size) {
                    val read = stream.read(bytes, total, bytes.size - total)
                    if (read <= 0) break
                    total += read
                }
                String(bytes, 0, total, Charsets.UTF_8)
            }
        } catch (error: IOException) {
            logger.debug("файл читалки $path не прочитан: ${error.message}", error)
            null
        } catch (error: SecurityException) {
            // Нет доступа ко всей памяти: читалки на общей памяти недоступны целиком, и это
            // штатное состояние flavor play, а не сбой.
            logger.debug("нет доступа к файлу читалки $path: ${error.message}", error)
            null
        }
    }

    override suspend fun children(directory: String): List<String> = withContext(dispatchers.io) {
        val dir = File(directory)
        if (!dir.isDirectory) return@withContext emptyList()
        try {
            dir.list()?.toList().orEmpty()
        } catch (error: SecurityException) {
            logger.debug("нет доступа к каталогу читалки $directory: ${error.message}", error)
            emptyList()
        }
    }

    internal companion object {

        /** Предел чтения: чужой файл прогресса — это килобайты, но полагаться на это нельзя. */
        const val MAX_TEXT_BYTES = 1_048_576
    }
}
