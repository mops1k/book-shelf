package io.github.bookshelf.core.metadata.writer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Запись правки метаданных в файл книги: выбор по формату.
 *
 * Поддерживаются форматы, которые сегодня читаются приложением, — FB2 (в том числе `.fb2.zip`) и
 * EPUB. Для остальных честно возвращается [MetadataWriteResult.NotSupported] с причиной: файл
 * остаётся нетронутым, а правка — в приложении.
 */
class BookFileMetadataWriter(
    private val fb2: Fb2MetadataWriter = Fb2MetadataWriter(),
    private val epub: EpubMetadataWriter = EpubMetadataWriter(),
) {

    fun rewrite(extension: String, bytes: ByteArray, edit: MetadataEdit): MetadataWriteResult =
        when (extension.trim().lowercase().removePrefix(".")) {
            FB2 -> fb2.rewrite(bytes, edit)
            FB2_ZIP -> rewriteFb2InZip(bytes, edit)
            EPUB -> epub.rewrite(bytes, edit)
            else -> MetadataWriteResult.NotSupported(
                "Правку нельзя записать в файл формата «$extension»: " +
                    "приложение умеет писать FB2 и EPUB",
            )
        }

    /**
     * `.fb2.zip` — тот же FB2, только упакованный: правится внутренний файл, архив собирается заново.
     *
     * Спецификация EPUB требует `mimetype` первой записью без сжатия; у `.fb2.zip` такого правила
     * нет, поэтому архив пересобирается обычным способом, а порядок записей сохраняется.
     */
    private fun rewriteFb2InZip(bytes: ByteArray, edit: MetadataEdit): MetadataWriteResult {
        val spooled = spool(bytes)
        try {
            val zip = runCatching { ZipFile(spooled) }.getOrNull()
                ?: return MetadataWriteResult.NotSupported("Файл не является ZIP-архивом")
            zip.use { archive ->
                val book = archive.entries().asSequence()
                    .firstOrNull { !it.isDirectory && it.name.lowercase().endsWith(FB2_SUFFIX) }
                    ?: return MetadataWriteResult.NotSupported("В архиве нет файла .fb2")
                val original = archive.getInputStream(book).use { it.readBytes() }
                val rewritten = when (val result = fb2.rewrite(original, edit)) {
                    is MetadataWriteResult.NotSupported -> return result
                    is MetadataWriteResult.Rewritten -> result.bytes
                }
                return MetadataWriteResult.Rewritten(rebuild(archive, book.name, rewritten))
            }
        } finally {
            spooled.delete()
        }
    }

    private fun rebuild(archive: ZipFile, bookName: String, book: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { target ->
            archive.entries().asSequence()
                .filterNot { entry -> entry.isDirectory }
                .forEach { entry ->
                    target.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == bookName) {
                        target.write(book)
                    } else {
                        archive.getInputStream(entry).use { source -> source.copyTo(target) }
                    }
                    target.closeEntry()
                }
        }
        return output.toByteArray()
    }

    /** `ZipFile` работает с файлом, а вход — байты: копия нужна, чтобы читать архив по частям. */
    private fun spool(bytes: ByteArray): File {
        val file = File.createTempFile("bookshelf-fb2-edit-", ".zip")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private companion object {
        const val FB2 = "fb2"
        const val FB2_ZIP = "fb2.zip"
        const val FB2_SUFFIX = ".fb2"
        const val EPUB = "epub"
    }
}
