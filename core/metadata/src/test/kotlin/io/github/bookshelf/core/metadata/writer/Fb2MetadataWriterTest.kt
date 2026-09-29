package io.github.bookshelf.core.metadata.writer

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.parser.impl.Fb2Parser
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Запись правки метаданных в сам FB2-файл.
 *
 * Фикстура — настоящая книга из Flibusta (`flibusta-890606.fb2.zip`, Бакстер «Кольцо»), та же, что
 * у парсера: синтетический FB2 написан по моему же пониманию формата и ошибок в нём не видно.
 *
 * Проверяется главное требование Александра (29.09.2026): пользователь поставил серию — и она
 * лежит в файле, поэтому переживёт перенос книги на другое устройство. Плюс два свойства, без
 * которых правка файла опасна: остальное содержимое книги не меняется (текст, обложка в base64)
 * и кодировка сохраняется (иначе cp1251-книга превратилась бы в кашу).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class Fb2MetadataWriterTest {

    private val writer = Fb2MetadataWriter()
    private val parser = Fb2Parser()

    @Test
    fun `edit lands in title-info and is read back by the parser`() = runTest {
        val original = realBook()

        val result = writer.rewrite(
            bytes = original,
            edit = MetadataEdit(
                title = "Кольцо II",
                authors = listOf("Стивен Бакстер", "Новый Автор"),
                seriesName = "Новая серия",
                seriesIndex = 7.0,
                description = "Правка пользователя",
            ),
        )

        val rewritten = (result as? MetadataWriteResult.Rewritten)?.bytes
        assertThat(rewritten).isNotNull()
        val metadata = parse(rewritten!!)
        assertThat(metadata?.title).isEqualTo("Кольцо II")
        assertThat(metadata?.authors).containsExactly("Стивен Бакстер", "Новый Автор").inOrder()
        assertThat(metadata?.seriesName).isEqualTo("Новая серия")
        assertThat(metadata?.seriesIndex).isEqualTo(7.0)
        assertThat(metadata?.description).isEqualTo("Правка пользователя")
    }

    @Test
    fun `everything outside title-info stays byte for byte`() = runTest {
        val original = realBook()
        val text = original.toString(Charsets.UTF_8)
        val before = text.substringBefore("<title-info>")
        val after = text.substringAfter("</title-info>")

        val rewritten = (writer.rewrite(original, MetadataEdit(seriesName = "Только серия")) as
            MetadataWriteResult.Rewritten).bytes.toString(Charsets.UTF_8)

        // Тело книги и обложка в base64 не должны даже перекодироваться: правка касается только
        // описания, и любое изменение остального текста — уже потеря данных.
        assertThat(rewritten.substringBefore("<title-info>")).isEqualTo(before)
        assertThat(rewritten.substringAfter("</title-info>")).isEqualTo(after)
        // Обложка из base64 по-прежнему разбирается.
        assertThat((parser.parse(inputOf(rewritten.toByteArray(Charsets.UTF_8))) as ParseResult.Success).cover)
            .isNotNull()
    }

    @Test
    fun `fields the user did not touch keep the values from the file`() = runTest {
        val original = realBook()

        val rewritten = (writer.rewrite(original, MetadataEdit(seriesName = "Только серия")) as
            MetadataWriteResult.Rewritten).bytes

        val metadata = parse(rewritten)
        assertThat(metadata?.title).isEqualTo("Кольцо")
        assertThat(metadata?.authors?.joinToString(" ")).contains("Бакстер")
        assertThat(metadata?.seriesName).isEqualTo("Только серия")
        // Описание в книге было — правка его не трогала, значит оно остаётся.
        assertThat(metadata?.description).isNotEmpty()
    }

    @Test
    fun `windows-1251 book stays in windows-1251`() = runTest {
        val cp1251 = toWindows1251(realBook())

        val rewritten = (writer.rewrite(cp1251, MetadataEdit(seriesName = "Серия для cp1251")) as
            MetadataWriteResult.Rewritten).bytes

        // Кодировка определяется декларацией: если писатель отдал UTF-8, разбор даст мусор.
        val text = String(rewritten, Charset.forName("windows-1251"))
        assertThat(text).contains("windows-1251")
        assertThat(text).contains("Серия для cp1251")
        assertThat(parse(rewritten)?.seriesName).isEqualTo("Серия для cp1251")
    }

    @Test
    fun `file without title-info is reported instead of being damaged`() = runTest {
        val notFb2 = "<html><body>не книга</body></html>".toByteArray(Charsets.UTF_8)

        val result = writer.rewrite(notFb2, MetadataEdit(seriesName = "Серия"))

        val unsupported = result as? MetadataWriteResult.NotSupported
        assertThat(unsupported).isNotNull()
        assertThat(unsupported?.reason).contains("title-info")
    }

    private suspend fun parse(bytes: ByteArray) =
        (parser.parse(inputOf(bytes)) as? ParseResult.Success)?.metadata

    private fun inputOf(bytes: ByteArray) = BookInput(
        fileName = "Bakster_Ksili_4_Kolco.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    /** FB2 внутри фикстуры: `.fb2.zip` распаковывается один раз, дальше работаем с самим FB2. */
    private fun realBook(): ByteArray {
        val archive = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("flibusta-890606.fb2.zip"),
        ) { "фикстура flibusta-890606.fb2.zip не найдена" }.readBytes()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            checkNotNull(zip.nextEntry) { "в фикстуре нет записей" }
            return zip.readBytes()
        }
    }

    /** Настоящая книга, перекодированная в windows-1251 вместе с декларацией. */
    private fun toWindows1251(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.UTF_8).replace("UTF-8", "windows-1251")
        return text.toByteArray(Charset.forName("windows-1251"))
    }
}
