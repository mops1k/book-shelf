package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Заглушка формата: CBR (нужен UnRar, лицензия не проходит F-Droid — только flavor `full`).
 *
 * Реализация этих парсеров отложена до релиза (решение Александра, 29.09.2026), но до тех пор файл
 * такого формата в библиотеке не имеет права ломать разбор: заглушка обязана отдать
 * [ParseResult.Unsupported], а не бросить `NotImplementedError` (именно это валило `MetadataWorker`).
 */
class StubParsersTest {

    private fun stubs(): List<Pair<BookMetadataParser, String>> = listOf(
        CbrParser(flags) to "cbr",
    )

    @Test
    fun `every stub reports the format as unsupported instead of throwing`() = runTest {
        stubs().forEach { (parser, extension) ->
            val result = parser.parse(input(extension))

            assertThat(result).isEqualTo(ParseResult.Unsupported)
        }
    }

    @Test
    fun `stubs still recognise their own files`() {
        // Поддержка формата объявлена — иначе реестр не выбрал бы парсер и книга получила бы
        // заголовок из имени файла молча, без пометки «формат пока не разбирается».
        stubs().forEach { (parser, extension) ->
            assertThat(parser.supports(extension, null)).isTrue()
        }
        assertThat(MobiParser().supports("azw3", null)).isTrue()
        assertThat(MobiParser().supports("prc", null)).isTrue()
    }

    /** Флаги сборки: CBR объявлен включённым, чтобы проверялась именно заглушка парсера. */
    private val flags = object : FeatureFlags {
        override val cbrEnabled: Boolean = true
        override val allowAllFilesAccess: Boolean = true
        override val opdsEnabled: Boolean = true
    }

    private fun input(extension: String) = BookInput(
        fileName = "book.$extension",
        extension = extension,
        mimeType = null,
        sizeBytes = 0L,
        openStream = { ByteArrayInputStream(ByteArray(0)) },
        openSeekable = { null },
        bytes = { _, _ -> ByteArray(0) },
    )
}
