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
     *
     * @param isCancelled спрашивается перед каждой картинкой: длинную книгу человек вправе
     *   остановить, а не ждать её конца (на e-ink книга в 13 МБ считается около минуты). При
     *   отмене возвращается «менять нечего» — файл не тронут, ничего не записано.
     */
    fun optimize(
        fileName: String,
        content: ByteArray,
        isCancelled: () -> Boolean = { false },
    ): Fb2Optimization
}

/**
 * Оптимизация FB2 и FB2.ZIP: минификация разметки, пересборка картинок, плотная упаковка архива.
 *
 * Порядок шагов и почему именно такой (один проход по книге):
 * 1. находятся блоки картинок ([Fb2Binaries]) и отпечатки их содержимого: одинаковые остаются в
 *    книге по одному разу, ссылки переводятся на оставшуюся копию ([Fb2BinaryDeduplicator]);
 * 2. разметка между блоками минифицируется ([Fb2XmlMinifier]) и уходит в результат сразу;
 * 3. каждая оставленная картинка пересобирается без потери пикселей ([Fb2ImageOptimizer]);
 * 4. если книга была в архиве, он переупаковывается с максимальным сжатием.
 *
 * **Почему один проход, а не четыре.** Прежняя версия превращала книгу в строку и делала три её
 * копии: исходную, минифицированную и пересобранную. На книге в 13,6 МБ это давало пик в 250 МБ
 * при потолке кучи 256 МБ — на устройстве систему убивала процесс (проверено 30.09.2026). Теперь
 * результат пишется в один байтовый буфер, а строка строится только для мелких частей.
 */
@Singleton
class DefaultFb2Optimizer @Inject constructor(
    private val images: Fb2ImageOptimizer,
) : Fb2Optimizer {

    private val minifier = Fb2XmlMinifier()

    private val deduplicator = Fb2BinaryDeduplicator()

    override fun optimize(
        fileName: String,
        content: ByteArray,
        isCancelled: () -> Boolean,
    ): Fb2Optimization {
        val lower = fileName.lowercase()
        val result = when {
            lower.endsWith(ZIP_EXTENSION) -> optimizeZip(content, isCancelled)
            lower.endsWith(FB2_EXTENSION) -> optimizeXml(content, isCancelled)
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

    /**
     * Пересобрать книгу FB2 одним проходом.
     *
     * Разметка между картинками минифицируется по частям, и края каждой части описываются байтами
     * соседей ([previousByte] и `nextByte`): иначе пробел на стыке обрабатывался бы не так, как
     * внутри целого документа.
     */
    private fun optimizeXml(content: ByteArray, isCancelled: () -> Boolean): ByteArray? = runCatching {
        imagesOptimized = 0
        val binaries = Fb2Binaries.scan(content)
        val redirect = deduplicator.duplicateIds(content, binaries)
        val out = ByteArrayOutputStream(content.size)
        var last = 0
        var lastByte = NO_BYTE
        for (binary in binaries) {
            // Отмена проверяется до очередной картинки: ждать конца книги человек не обязан.
            if (isCancelled()) return null
            lastByte = writeMarkup(out, content, last, binary.start, binary.start, lastByte, redirect)
            last = binary.end
            // Дубликат выброшен: его содержимое уже лежит в оставленной копии, а ссылки переведены.
            if (binary.id != null && redirect.containsKey(binary.id)) continue
            val block = optimizedBlock(content, binary)
            out.write(block)
            lastByte = block[block.size - 1].toInt() and BYTE_MASK
        }
        writeMarkup(out, content, last, content.size, NO_BYTE, lastByte, redirect)
        out.toByteArray()
    }.getOrNull()

    /** Записать кусок разметки и вернуть его последний байт (нужен следующему куску). */
    private fun writeMarkup(
        out: ByteArrayOutputStream,
        content: ByteArray,
        from: Int,
        to: Int,
        nextByte: Int,
        previousByte: Int,
        redirect: Map<String, String>,
    ): Int {
        val minified = minifier.minify(
            source = content,
            from = from,
            to = to,
            previousByte = previousByte,
            nextByte = nextByte,
        )
        val markup = deduplicator.rewriteReferences(minified, redirect)
        out.write(markup)
        return if (markup.isEmpty()) previousByte else markup[markup.size - 1].toInt() and BYTE_MASK
    }

    /**
     * Блок картинки в результате.
     *
     * Содержимое — base64, поэтому картинка декодируется, оптимизируется и кодируется обратно одной
     * строкой: переносы строк в base64 ничего не значат, а дефлейту мешают. Атрибуты копируются
     * байт-в-байт: разбирать их в строку незачем, а испортить ими файл — легко.
     */
    private fun optimizedBlock(content: ByteArray, binary: Fb2Binary): ByteArray {
        val payload = content.copyOfRange(binary.payloadStart, binary.payloadEnd)
        val decoded = runCatching { Base64.getMimeDecoder().decode(payload) }.getOrNull()
        val optimized = decoded?.let { images.optimize(it, binary.contentType) }
        if (optimized == null) return content.copyOfRange(binary.start, binary.end)
        imagesOptimized += 1
        val out = ByteArrayOutputStream(binary.end - binary.start)
        out.write(OPEN_BINARY)
        out.write(content, binary.start + OPEN_BINARY.size, binary.payloadStart - 1 - binary.start - OPEN_BINARY.size)
        out.write(GT_BYTE)
        out.write(Base64.getEncoder().encode(optimized))
        out.write(CLOSE_BINARY)
        return out.toByteArray()
    }

    private fun optimizeZip(content: ByteArray, isCancelled: () -> Boolean): ByteArray? = runCatching {
        val out = ByteArrayOutputStream(content.size)
        ZipOutputStream(out).use { zip ->
            zip.setLevel(BEST_COMPRESSION)
            ZipInputStream(content.inputStream()).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    if (isCancelled()) return null
                    val name = entry.name
                    val raw = input.readBytes()
                    val optimized = if (name.lowercase().endsWith(FB2_EXTENSION)) {
                        optimizeXml(raw, isCancelled) ?: raw
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
        const val BYTE_MASK = 0xFF

        /** «Байта нет»: начало документа. */
        const val NO_BYTE = -1

        val OPEN_BINARY = "<binary".toByteArray(Charsets.US_ASCII)
        val CLOSE_BINARY = "</binary>".toByteArray(Charsets.US_ASCII)
        val GT_BYTE = byteArrayOf('>'.code.toByte())
    }
}
