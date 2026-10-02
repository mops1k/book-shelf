package io.github.bookshelf.core.metadata.registry

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.FakeFeatureFlags
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.impl.CbrParser
import io.github.bookshelf.core.metadata.parser.impl.CbzParser
import io.github.bookshelf.core.metadata.parser.impl.DocxParser
import io.github.bookshelf.core.metadata.parser.impl.EpubParser
import io.github.bookshelf.core.metadata.parser.impl.Fb2Parser
import io.github.bookshelf.core.metadata.parser.impl.MobiParser
import io.github.bookshelf.core.metadata.parser.impl.OdtParser
import io.github.bookshelf.core.metadata.parser.impl.PdfParser
import io.github.bookshelf.core.metadata.parser.impl.RtfParser
import io.github.bookshelf.core.metadata.parser.impl.TxtParser
import org.junit.Test

/**
 * Выбор парсера — реальная логика «расширение → MIME → магия», поэтому покрыта JUnit-тестами.
 * Главный кейс из «Форматы» §2: FB2 с неверным расширением `.txt` обязан определиться
 * по сигнатуре, а не уйти в текстовый парсер.
 */
class ParserRegistryTest {

    private val registry = DefaultParserRegistry(ALL_PARSERS)

    @Test
    fun fb2_with_wrong_txt_extension_is_detected_by_magic() {
        val fb2Prefix = (
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">"""
            ).toByteArray(Charsets.US_ASCII)

        assertThat(registry.select(extension = "txt", mimeType = null, prefix = fb2Prefix)?.id)
            .isEqualTo("fb2")
    }

    @Test
    fun pdf_with_wrong_fb2_extension_is_detected_by_magic() {
        val pdfPrefix = "%PDF-1.6".toByteArray(Charsets.US_ASCII)

        assertThat(registry.select("fb2", null, pdfPrefix)?.id).isEqualTo("pdf")
    }

    @Test
    fun extension_selects_parser_when_magic_is_unknown() {
        assertThat(registry.select("mobi", null, ByteArray(EMPTY_PREFIX))?.id).isEqualTo("mobi")
    }

    @Test
    fun epub_inside_zip_container_is_resolved_by_extension() {
        assertThat(registry.select("epub", null, ZIP_PREFIX)?.id).isEqualTo("epub")
    }

    @Test
    fun fb2_zip_inside_zip_container_is_resolved_by_compound_extension() {
        assertThat(registry.select("fb2.zip", null, ZIP_PREFIX)?.id).isEqualTo("fb2")
    }

    @Test
    fun zip_with_unknown_extension_has_no_parser() {
        assertThat(registry.select("txt", null, ZIP_PREFIX)).isNull()
    }

    @Test
    fun mime_is_used_when_extension_is_empty() {
        assertThat(registry.select("", "application/pdf", ByteArray(EMPTY_PREFIX))?.id).isEqualTo("pdf")
    }

    @Test
    fun djvu_is_recognized_by_magic_but_has_no_parser() {
        val djvuPrefix = "AT&T".toByteArray(Charsets.US_ASCII)

        assertThat(registry.select("djvu", null, djvuPrefix)).isNull()
    }

    @Test
    fun all_returns_every_registered_parser() {
        assertThat(registry.all()).hasSize(PARSER_COUNT)
    }

    private companion object {
        const val EMPTY_PREFIX = 0
        const val PARSER_COUNT = 10

        val ZIP_PREFIX = "PK\u0003\u0004".toByteArray(Charsets.ISO_8859_1)

        val ALL_PARSERS: Set<BookMetadataParser> = setOf(
            Fb2Parser(),
            EpubParser(),
            PdfParser(),
            TxtParser(),
            MobiParser(),
            CbzParser(),
            CbrParser(FakeFeatureFlags()),
            DocxParser(),
            OdtParser(),
            RtfParser(),
        )
    }
}
