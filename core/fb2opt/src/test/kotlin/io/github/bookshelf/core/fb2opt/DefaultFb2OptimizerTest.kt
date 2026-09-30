package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Оптимизация целой книги FB2 (`DefaultFb2Optimizer`): `.fb2` и `.fb2.zip`.
 *
 * Проверяется обещание, ради которого фича делается: файл становится меньше, **но читается так же**.
 * Поэтому после оптимизации проверяется не только размер, но и содержимое: разметка минифицирована,
 * а картинка после декодирования остаётся той же (совпадают сжатые данные после `SOS`).
 *
 * Фикстура `book-small.fb2` собрана из настоящей книги («Книга Уткина Мы тут жизнь живём»):
 * шапка, описание, раздел с текстом и картинка взяты как есть, ничего не выдумано.
 *
 * Отдельно проверяется правило оригинала: **заменяем только если стало меньше**. Книга, которую
 * сжать не удалось, обязана вернуться без изменений — иначе читалка получила бы другой файл ни за что.
 */
class DefaultFb2OptimizerTest {

    private val optimizer = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer()))

    @Test
    fun `a plain fb2 book becomes smaller and keeps its picture`() {
        val source = fixture("book-small.fb2")

        val result = optimizer.optimize("book.fb2", source)

        assertThat(result.changed).isTrue()
        assertThat(result.afterBytes).isLessThan(result.beforeBytes)
        val optimized = String(result.bytes!!, Charsets.UTF_8)
        // Отступы между тегами ушли, текст остался.
        assertThat(optimized).doesNotContain("\n  <section>")
        assertThat(optimized).contains("Мы тут жизнь живём")
        // Пиксели те же: сравниваются коэффициенты картинки (то, что и определяет изображение).
        // Байты сравнивать нельзя — пережатие таблицами Хаффмана их как раз и меняет.
        assertThat(optimized.imageBytes().coefficients())
            .isEqualTo(String(source, Charsets.UTF_8).imageBytes().coefficients())
    }

    @Test
    fun `a zipped book is repacked smaller and stays readable`() {
        val source = zipOf("book.fb2", fixture("book-small.fb2"))

        val result = optimizer.optimize("book.fb2.zip", source)

        assertThat(result.changed).isTrue()
        assertThat(result.afterBytes).isLessThan(result.beforeBytes)
        val unpacked = unzip(result.bytes!!).getValue("book.fb2")
        assertThat(String(unpacked, Charsets.UTF_8)).contains("Мы тут жизнь живём")
    }

    @Test
    fun `a book that cannot shrink is left untouched`() {
        val alreadyMinimal = fixture("book-small.fb2").let { optimizer.optimize("book.fb2", it) }
        check(alreadyMinimal.changed) { "фикстура обязана сжиматься, иначе тест бессмысленен" }

        // Второй проход по уже сжатой книге: выигрыша нет — заменять нечем.
        val second = optimizer.optimize("book.fb2", alreadyMinimal.bytes!!)

        assertThat(second.changed).isFalse()
    }

    @Test
    fun `a broken archive is left untouched`() {
        val broken = "не архив".toByteArray()

        assertThat(optimizer.optimize("book.fb2.zip", broken).changed).isFalse()
    }

    @Test
    fun `an unknown file type is left untouched`() {
        val source = fixture("book-small.fb2")

        assertThat(optimizer.optimize("book.pdf", source).changed).isFalse()
    }

    // --- вспомогательное ---

    /** Байты картинки из книги: base64 внутри первого `<binary>` разбирается обратно. */
    private fun String.imageBytes(): ByteArray {
        val start = indexOf("<binary")
        val open = indexOf('>', start) + 1
        val close = indexOf("</binary>", open)
        val base64 = substring(open, close).filterNot { it.isWhitespace() }
        return java.util.Base64.getDecoder().decode(base64)
    }

    /** Коэффициенты картинки: то, что определяет пиксели. */
    private fun ByteArray.coefficients(): List<List<Short>> {
        val structure = checkNotNull(JpegParser.parse(this)) { "картинка не разбирается" }
        val decoded = checkNotNull(JpegEntropyTranscoder(structure).decode()) { "данные не читаются" }
        return decoded.blocks.map { blocks -> blocks.flatMap { block -> block.toList() } }
    }

    private fun zipOf(name: String, content: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun fixture(name: String): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/$name"),
    ) { "нет фикстуры $name" }.use { stream -> stream.readBytes() }
}
