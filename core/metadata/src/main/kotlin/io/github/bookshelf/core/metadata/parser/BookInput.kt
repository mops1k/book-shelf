package io.github.bookshelf.core.metadata.parser

import java.io.InputStream
import java.nio.channels.FileChannel

/**
 * Входные данные парсера — ровно тот контракт, что зафиксирован в docs/architecture.md §4.4.
 *
 * [openStream] отдаёт одноразовый поток (FB2/TXT/OPF читаются потоково), [openSeekable] —
 * канал с произвольным доступом для MOBI/AZW3, PDF и CBZ/CBR. Для облачных DocumentsProvider
 * канал недоступен, и парсер обязан деградировать до `Partial` без обложки, но не падать
 * (docs/formats.md §2, §7).
 *
 * @param fileName имя файла как его видит пользователь — источник заголовка при `Partial`;
 * @param extension расширение в нижнем регистре, возможно составное (`fb2.zip`);
 * @param mimeType MIME от провайдера; часто `null` или неверный;
 * @param sizeBytes размер файла, если известен;
 * @param bytes чтение диапазона `[offset, offset + length)` без сохранения позиции.
 */
class BookInput(
    val fileName: String,
    val extension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val openStream: () -> InputStream,
    val openSeekable: () -> FileChannel?,
    val bytes: suspend (offset: Long, length: Int) -> ByteArray,
)
