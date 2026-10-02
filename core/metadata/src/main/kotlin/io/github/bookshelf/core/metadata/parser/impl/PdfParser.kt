package io.github.bookshelf.core.metadata.parser.impl

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.bookshelf.core.common.io.SeekableFile
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PDF — количество страниц и обложка первой страницы через [PdfRenderer].
 *
 * Метаданные намеренно ограничены именем файла: `PdfRenderer` не предоставляет API для чтения
 * `documentInfo`, а тяжёлый `pdfbox-android` сознательно отклонён в `docs/formats.md` §5.
 * Поэтому не добавляем фиктивных сведений об авторе или описании PDF.
 *
 * `PdfRenderer` требует [ParcelFileDescriptor], а контракт [BookInput] отдаёт [FileChannel].
 * Канал (или поток для pipe-источника) копируем во временный файл, открываем его только на чтение
 * и удаляем после разбора. Это сохраняет ограниченную память и позволяет следовать API Android.
 * Положительный путь обложки нужно проверять на устройстве: Robolectric не воспроизводит нативный
 * рендерер одинаково с Android (см. docs/formats.md §9).
 */
class PdfParser(
    /**
     * Предел размера временной копии документа.
     *
     * `PdfRenderer` работает только с файлом, поэтому документ копируется целиком; без предела
     * файл-гигант занимал хранилище (находка аудита 28.09.2026). Предел настраиваемый: тест
     * проверяет его маленьким значением.
     */
    private val maxCopyBytes: Long = MAX_COPY_BYTES,
) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            mimeType?.substringBefore(';')?.trim()?.lowercase()?.let { it in MIME_TYPES } == true

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val temporaryFile = copyToTemporaryFile(input)
            try {
                parsePdf(temporaryFile, input.fileName)
            } finally {
                temporaryFile.delete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PdfFailure) {
            ParseResult.Failed(error.message ?: "PDF: отказ разбора", error.kind)
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
            ParseResult.Failed("PDF: не удалось открыть документ: $detail")
        }
    }

    private fun parsePdf(file: File, fileName: String): ParseResult {
        if (!looksLikePdf(file)) {
            return ParseResult.Failed("PDF: неверная сигнатура файла")
        }

        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (security: SecurityException) {
                return ParseResult.Partial(
                    metadata = metadataFromFileName(fileName, pageCount = null),
                    reason = "PDF: документ защищён: ${security.message ?: "нужен пароль"}",
                )
            }

            try {
                val pageCount = renderer.pageCount
                if (pageCount <= 0) {
                    return ParseResult.Failed("PDF: документ не содержит страниц")
                }

                val metadata = metadataFromFileName(fileName, pageCount)
                val cover = try {
                    renderCover(renderer)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    return ParseResult.Partial(
                        metadata = metadata,
                        reason = "PDF: не удалось получить обложку: ${error.describe()}",
                    )
                }

                if (cover == null) {
                    ParseResult.Partial(metadata, "PDF: обложка не получена")
                } else {
                    ParseResult.Success(metadata, cover)
                }
            } finally {
                runCatching { renderer.close() }
            }
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun metadataFromFileName(fileName: String, pageCount: Int?): BookMetadata {
        val title = fileName.substringBeforeLast('.', fileName).trim()
        return BookMetadata(
            title = title.ifEmpty { fileName.trim() },
            pageCount = pageCount,
            source = MetadataOrigin.FILENAME,
        )
    }

    /**
     * Декодирует страницу 0 в JPEG для [CoverBytes].
     *
     * Это единственный участок с настоящим `PdfRenderer`-рендерингом, поэтому положительный
     * результат нельзя подтвердить обычным unit-тестом: Robolectric не воспроизводит нативный
     * PDF-движок как устройство. `PdfParserTest` намеренно проверяет только ветку отказа
     * (недоступный канал/поток и понятный `reason`); успешный путь проверяется на устройстве.
     */
    private fun renderCover(renderer: PdfRenderer): CoverBytes? {
        val page = renderer.openPage(0)
        try {
            if (page.width <= 0 || page.height <= 0) return null

            val scale = min(
                1f,
                MAX_COVER_DIMENSION.toFloat() / max(page.width, page.height).toFloat(),
            )
            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            try {
                // Битмап по умолчанию ПРОЗРАЧНЫЙ, а `PdfRenderer` рисует страницу без фона: у PDF
                // без белого прямоугольника на всю страницу результат остаётся прозрачным, а JPEG
                // прозрачность превращает в чёрный. На устройстве 28.09.2026 обложка такой книги
                // была сплошным чёрным прямоугольником. Белая подложка — не косметика, а условие
                // корректного рендера.
                bitmap.eraseColor(Color.WHITE)
                val matrix = Matrix().apply { postScale(scale, scale) }
                page.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )

                val output = java.io.ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, output)) return null
                val bytes = output.toByteArray()
                if (bytes.isEmpty()) return null
                return CoverBytes(
                    bytes = bytes,
                    mimeType = COVER_MIME_TYPE,
                    width = width,
                    height = height,
                )
            } finally {
                bitmap.recycle()
            }
        } finally {
            runCatching { page.close() }
        }
    }

    private fun copyToTemporaryFile(input: BookInput): File {
        // Заявленный размер известен заранее: гигантский документ отвергается до копирования.
        input.sizeBytes?.let { size ->
            if (size > maxCopyBytes) throw tooLarge(size)
        }
        val temporaryFile = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX)
        val copied = tryCopyChannel(input, temporaryFile)
        if (copied) {
            checkCopySize(temporaryFile)
            return temporaryFile
        }

        temporaryFile.delete()
        return try {
            input.openStream().use { stream ->
                FileOutputStream(temporaryFile).use { output -> stream.copyTo(output) }
            }
            checkCopySize(temporaryFile)
            temporaryFile
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    /** Провайдер вправе не сообщать размер: тогда предел проверяется по факту копии. */
    private fun checkCopySize(file: File) {
        if (file.length() > maxCopyBytes) throw tooLarge(file.length())
    }

    private fun tooLarge(bytes: Long): PdfFailure =
        PdfFailure("PDF: файл превышает допустимый размер: $bytes байт", ParseFailureKind.ARCHIVE_LIMIT)

    /** Отказ разбора с видом: без него превышение лимита выглядело бы как битый файл. */
    private class PdfFailure(
        message: String,
        val kind: ParseFailureKind = ParseFailureKind.CORRUPT_FILE,
    ) : Exception(message)

    private fun tryCopyChannel(input: BookInput, destination: File): Boolean {
        val seekable = try {
            input.openSeekable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return false

        return try {
            FileOutputStream(destination).use { output -> copyChannel(seekable.channel, output) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        } finally {
            seekable.close()
        }
    }

    private fun copyChannel(channel: FileChannel, output: FileOutputStream) {
        val buffer = ByteBuffer.allocate(READ_BUFFER_BYTES)
        while (true) {
            buffer.clear()
            val count = channel.read(buffer)
            if (count < 0) break
            if (count == 0) break
            buffer.flip()
            while (buffer.hasRemaining()) output.write(buffer.get().toInt() and BYTE_MASK)
        }
    }

    private fun looksLikePdf(file: File): Boolean {
        return FileInputStream(file).use { stream ->
            val prefix = ByteArray(PDF_SIGNATURE_LENGTH)
            var offset = 0
            while (offset < prefix.size) {
                val count = stream.read(prefix, offset, prefix.size - offset)
                if (count < 0) break
                if (count == 0) break
                offset += count
            }
            String(prefix, 0, offset, Charsets.US_ASCII) == PDF_SIGNATURE
        }
    }

    private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"

    private companion object {
        const val ID = "pdf"
        const val SPRINT = 3
        const val TEMP_PREFIX = "bookshelf-pdf-"
        const val TEMP_SUFFIX = ".pdf"

        /**
         * Предел временной копии PDF — 300 МБ.
         *
         * Сканы бывают крупными, поэтому предел выше, чем у копии книги; но без предела файл-гигант
         * из облачного источника занимал бы хранилище целиком.
         */
        const val MAX_COPY_BYTES = 300L * 1_024 * 1_024
        const val READ_BUFFER_BYTES = 8 * 1024
        const val BYTE_MASK = 0xFF
        const val MAX_COVER_DIMENSION = 600
        const val COVER_QUALITY = 80
        const val COVER_MIME_TYPE = "image/jpeg"
        const val PDF_SIGNATURE_LENGTH = 5
        const val PDF_SIGNATURE = "%PDF-"
        val EXTENSIONS = setOf("pdf")
        val MIME_TYPES = setOf("application/pdf")
    }
}
