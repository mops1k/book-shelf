package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.rar.RarArchive
import io.github.bookshelf.core.metadata.rar.RarEntry
import io.github.bookshelf.core.metadata.rar.RarNotSeekableException
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CBR — RAR с `ComicInfo.xml` и страницами-картинками («Форматы» §7).
 *
 * Формат жил заглушкой дольше остальных: разбор RAR требует библиотеки под несвободной
 * UnRar License, поэтому junrar подключается только во flavor `full` (ADR-007), а парсер
 * получает ЧТЕНИЕ архива портом [RarArchive]. Проверяем поведение парсера на фейке порта:
 * сборки без junrar обязаны честно не предлагать формат, а не падать.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CbrParserTest {

    @Test
    fun `comic info gives title, authors, series and description`() = runTest {
        val archive = FakeRarArchive(
            "ComicInfo.xml" to COMIC_INFO.toByteArray(),
            "page1.png" to PNG_ONE,
            "page2.png" to PNG_TWO,
        )

        val result = parser(archive).parse(input())

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Приключения Флинкса")
        assertThat(success.metadata.authors).containsExactly("Алан Дин Фостер")
        assertThat(success.metadata.seriesName).isEqualTo("Приключения Флинкса")
        assertThat(success.metadata.seriesIndex).isEqualTo(3.0)
        assertThat(success.metadata.description).isEqualTo("Первый выпуск")
        assertThat(success.metadata.publisher).isEqualTo("Азбука")
        assertThat(success.metadata.publishedYear).isEqualTo(2024)
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.pageCount).isEqualTo(2)
        assertThat(success.metadata.tags).containsExactly("Фантастика")
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
        assertThat(success.cover?.bytes).isNotEmpty()
    }

    @Test
    fun `paths written with backslashes do not hide comic info or pages`() = runTest {
        // Настоящие CBR собирают на Windows: в именах записей встречаются обратные слэши
        // (проверено на живом файле: «Amazing Man 022\Amazing Man 022 001.jpg»).
        val archive = FakeRarArchive(
            "comics\\ComicInfo.xml" to COMIC_INFO.toByteArray(),
            "comics\\page1.png" to PNG_ONE,
        )

        val result = parser(archive).parse(input())

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Приключения Флинкса")
        // Обложка нашлась по имени с обратным слэшем — значит, и ComicInfo, и страницы видны.
        assertThat(success.cover?.bytes).isEqualTo(PNG_ONE)
    }

    @Test
    fun `the first page in natural order becomes the cover`() = runTest {
        // Обычная сортировка поставила бы page10.png раньше page2.png — обложкой стала бы десятая.
        val archive = FakeRarArchive(
            "page10.png" to PNG_TEN,
            "page2.png" to PNG_TWO,
        )

        val result = parser(archive).parse(input())

        val partial = result as ParseResult.Partial
        assertThat(partial.cover?.bytes).isEqualTo(PNG_TWO)
    }

    @Test
    fun `without comic info the archive still gives a cover and a filename title`() = runTest {
        val archive = FakeRarArchive("page1.png" to PNG_ONE)

        val result = parser(archive).parse(input(fileName = "Amazing Man 022.cbr"))

        val partial = result as ParseResult.Partial
        assertThat(partial.metadata.title).isEqualTo("Amazing Man 022")
        assertThat(partial.metadata.pageCount).isEqualTo(1)
        assertThat(partial.reason).contains("ComicInfo")
        assertThat(partial.cover?.bytes).isEqualTo(PNG_ONE)
    }

    @Test
    fun `an archive without comic info and pages fails with a clear reason`() = runTest {
        val archive = FakeRarArchive("readme.txt" to "ничего".toByteArray())

        val result = parser().parse(input())

        val failed = result as ParseResult.Failed
        assertThat(failed.kind).isEqualTo(ParseFailureKind.CORRUPT_FILE)
        assertThat(failed.reason).contains("CBR")
    }

    @Test
    fun `a book without random access degrades to partial instead of failing`() = runTest {
        // Облачный провайдер отдаёт pipe: произвольного чтения нет, и RAR не распаковать.
        val archive = FakeRarArchive(failure = RarNotSeekableException())

        val result = parser(archive).parse(input(fileName = "Комикс.cbr"))

        val partial = result as ParseResult.Partial
        assertThat(partial.metadata.title).isEqualTo("Комикс")
        assertThat(partial.cover).isNull()
        assertThat(partial.reason).contains("произвольн")
    }

    @Test
    fun `an entry that is declared but comes back empty is a failure, not a missing file`() = runTest {
        // Так ведёт себя junrar, когда распаковка записи не удалась: поток приходит пустым,
        // а ошибка молча гасится. Сказать «ComicInfo.xml не найден» значило бы соврать.
        val archive = FakeRarArchive("ComicInfo.xml" to ByteArray(0), "page1.png" to PNG_ONE)
        archive.sizes = mapOf("ComicInfo.xml" to 3_988L)

        val result = parser(archive).parse(input())

        val failed = result as ParseResult.Failed
        assertThat(failed.reason).contains("ComicInfo.xml")
        assertThat(failed.kind).isEqualTo(ParseFailureKind.CORRUPT_FILE)
    }

    @Test
    fun `a broken archive fails instead of throwing`() = runTest {
        val archive = FakeRarArchive(failure = IllegalArgumentException("битый заголовок"))

        val result = parser(archive).parse(input())

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
    }

    @Test
    fun `a build without a rar reader does not offer the format`() = runTest {
        val parser = CbrParser(featureFlags = enabledFlags, readers = emptySet())

        assertThat(parser.supports("cbr", null)).isFalse()
        assertThat(parser.parse(input())).isEqualTo(ParseResult.Unsupported)
    }

    @Test
    fun `a build with the flag off does not offer the format`() = runTest {
        val parser = CbrParser(featureFlags = disabledFlags, readers = listOf(FakeRarArchive()))

        assertThat(parser.supports("cbr", "application/vnd.comicbook-rar")).isFalse()
        assertThat(parser.parse(input())).isEqualTo(ParseResult.Unsupported)
    }

    private fun parser(archive: RarArchive = FakeRarArchive()): CbrParser =
        CbrParser(featureFlags = enabledFlags, readers = listOf(archive))

    private fun input(fileName: String = "comic.cbr") = BookInput(
        fileName = fileName,
        extension = "cbr",
        mimeType = "application/vnd.comicbook-rar",
        sizeBytes = 1_024L,
        openStream = { ByteArrayInputStream(ByteArray(0)) },
        openSeekable = { null },
        bytes = { _, _ -> ByteArray(0) },
    )

    /**
     * Фейк порта чтения RAR (ADR-012: свои типы не мокаются).
     *
     * Записи отдаются в том же порядке, в каком их отдал бы архив.
     */
    private class FakeRarArchive(
        vararg entries: Pair<String, ByteArray>,
        private val failure: Exception? = null,
    ) : RarArchive {

        private val entries = entries.toList()

        override fun forEachEntry(input: BookInput, action: (RarEntry, () -> InputStream) -> Unit) {
            failure?.let { throw it }
            entries.forEach { (name, bytes) ->
                val declared = sizes[name] ?: bytes.size.toLong()
                action(RarEntry(name = name, sizeBytes = declared)) { ByteArrayInputStream(bytes) }
            }
        }

        /** Размер, который запись объявляет: нужен проверке «запись есть, но не читается». */
        var sizes: Map<String, Long> = emptyMap()
    }

    /** Флаги сборки: CBR включён только во flavor `full` (ADR-007). */
    private val enabledFlags = object : FeatureFlags {
        override val cbrEnabled: Boolean = true
        override val allowAllFilesAccess: Boolean = true
        override val opdsEnabled: Boolean = true
    }

    private val disabledFlags = object : FeatureFlags {
        override val cbrEnabled: Boolean = false
        override val allowAllFilesAccess: Boolean = true
        override val opdsEnabled: Boolean = true
    }

    private companion object {
        val COMIC_INFO = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
              <Title>Приключения Флинкса</Title>
              <Series>Приключения Флинкса</Series>
              <Number>3</Number>
              <Writer>Алан Дин Фостер</Writer>
              <Publisher>Азбука</Publisher>
              <Year>2024</Year>
              <LanguageISO>ru</LanguageISO>
              <PageCount>2</PageCount>
              <Genre>Фантастика</Genre>
              <Summary>Первый выпуск</Summary>
            </ComicInfo>
        """.trimIndent()

        /** Настоящие PNG-заголовки не нужны: парсер сравнивает байты, а не декодирует картинку. */
        val PNG_ONE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1)
        val PNG_TWO = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 2)
        val PNG_TEN = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 10)
    }
}

