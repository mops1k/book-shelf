package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Итог оптимизации книги.
 *
 * @param bytes новый файл книги; `null` — оптимизировать не удалось или выигрыша нет, и тогда
 *   [changed] = false, а исходный файл обязан остаться на месте.
 * @param beforeBytes размер исходного файла.
 * @param afterBytes размер нового файла (или исходного, если замены нет).
 * @param images сколько картинок пересобрано.
 */
data class Fb2Optimization(
    val bytes: ByteArray?,
    val beforeBytes: Int,
    val afterBytes: Int,
    val images: Int = 0,
) {
    /** Есть что заменять: новый файл строго меньше исходного. */
    val changed: Boolean get() = bytes != null

    /** Сколько байт сэкономлено. */
    val savedBytes: Int get() = (beforeBytes - afterBytes).coerceAtLeast(0)
}

/**
 * Оптимизация книги FB2 без потери данных.
 *
 * Заменяет исходный файл только если новый **строго меньше** — это правило оригинала
 * (fb2opt) и единственная защита от «оптимизации», которая делает книгу хуже.
 */
interface Fb2Optimizer {

    /**
     * Оптимизировать книгу [fileName] с содержимым [content].
     *
     * Ошибки не выходят наружу: непонятный или битый файл возвращается как «менять нечего» —
     * оптимизация не должна ломать библиотеку из-за одной странной книги.
     */
    fun optimize(fileName: String, content: ByteArray): Fb2Optimization
}

/**
 * Оптимизация FB2 и FB2.ZIP: минификация разметки, пересборка картинок, плотная упаковка архива.
 *
 * Порядок шагов и почему именно такой:
 * 1. разметка минифицируется ([Fb2XmlMinifier]) — комментарии и отступы не нужны читателю;
 * 2. каждая картинка внутри `<binary>` пересобирается без потери пикселей ([Fb2ImageOptimizer]);
 * 3. если книга была в архиве, он переупаковывается с максимальным сжатием.
 *
 * Работа идёт в памяти: оптимизацию запускает человек для одной книги, и он же видит прогресс.
 */
@Singleton
class DefaultFb2Optimizer @Inject constructor(
    private val images: Fb2ImageOptimizer,
) : Fb2Optimizer {

    private val minifier = Fb2XmlMinifier()

    override fun optimize(fileName: String, content: ByteArray): Fb2Optimization {
        val lower = fileName.lowercase()
        val result = when {
            lower.endsWith(ZIP_EXTENSION) -> optimizeZip(content)
            lower.endsWith(FB2_EXTENSION) -> optimizeXml(content)
            else -> null
        }
        val before = content.size
        val optimized = result?.takeIf { it.size < before }
        return Fb2Optimization(
            bytes = optimized,
            beforeBytes = before,
            afterBytes = optimized?.size ?: before,
            images = if (optimized == null) 0 else imagesOptimized,
        )
    }

    /** Сколько картинок пересобрано в последнем вызове (для отчёта человеку). */
    private var imagesOptimized: Int = 0

    private fun optimizeXml(content: ByteArray): ByteArray? = runCatching {
        val xml = String(content, Charsets.UTF_8)
        imagesOptimized = 0
        val rewritten = replaceImages(minifier.minify(xml))
        rewritten.toByteArray(Charsets.UTF_8)
    }.getOrNull()

    /**
     * Пересобрать картинки внутри `<binary>`.
     *
     * Содержимое — base64, поэтому картинка декодируется, оптимизируется и кодируется обратно одной
     * строкой: переносы строк в base64 ничего не значат, а дефлейту мешают.
     */
    private fun replaceImages(xml: String): String {
        val matcher = BINARY.findAll(xml)
        if (!matcher.any()) return xml
        val out = StringBuilder(xml.length)
        var last = 0
        BINARY.findAll(xml).forEach { match ->
            out.append(xml, last, match.range.first)
            last = match.range.last + 1
            val attributes = match.groupValues[1]
            val contentType = CONTENT_TYPE.find(attributes)?.groupValues?.get(1).orEmpty()
            val original = match.groupValues[2]
            val decoded = runCatching {
                Base64.getMimeDecoder().decode(original)
            }.getOrNull()
            val optimized = decoded?.let { images.optimize(it, contentType) }
            if (optimized == null) {
                out.append(match.value)
            } else {
                imagesOptimized += 1
                out.append("<binary").append(attributes).append('>')
                out.append(Base64.getEncoder().encodeToString(optimized))
                out.append("</binary>")
            }
        }
        out.append(xml, last, xml.length)
        return out.toString()
    }

    private fun optimizeZip(content: ByteArray): ByteArray? = runCatching {
        val out = ByteArrayOutputStream(content.size)
        ZipOutputStream(out).use { zip ->
            zip.setLevel(BEST_COMPRESSION)
            ZipInputStream(content.inputStream()).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val raw = input.readBytes()
                    val optimized = if (name.lowercase().endsWith(FB2_EXTENSION)) {
                        optimizeXml(raw) ?: raw
                    } else {
                        raw
                    }
                    zip.putNextEntry(ZipEntry(name).apply { time = entry.time })
                    zip.write(optimized)
                    zip.closeEntry()
                    entry = input.nextEntry
                }
            }
        }
        out.toByteArray()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val FB2_EXTENSION = ".fb2"
        const val ZIP_EXTENSION = ".zip"
        const val BEST_COMPRESSION = 9
        val BINARY = Regex("<binary([^>]*)>(.*?)</binary>", RegexOption.DOT_MATCHES_ALL)
        val CONTENT_TYPE = Regex("content-type=\"([^\"]+)\"")
    }
}
