package io.github.bookshelf.core.metadata.binary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * «Магия» байт — чистая таблица сигнатур («Форматы» §2), поэтому проверяется без Android.
 */
class MagicBytesTest {

    @Test
    fun detects_pdf() {
        assertThat(MagicBytes.sniff("%PDF-1.7".toByteArray(Charsets.US_ASCII))).isEqualTo(FormatHint.PDF)
    }

    @Test
    fun detects_zip_container() {
        val prefix = "PK\u0003\u0004\u0014\u0000".toByteArray(Charsets.ISO_8859_1)
        assertThat(MagicBytes.sniff(prefix)).isEqualTo(FormatHint.ZIP_CONTAINER)
    }

    @Test
    fun detects_mobi_at_palm_signature_offset() {
        val prefix = ByteArray(MOBI_SIGNATURE_OFFSET) + "BOOKMOBI".toByteArray(Charsets.US_ASCII)
        assertThat(MagicBytes.sniff(prefix)).isEqualTo(FormatHint.MOBI)
    }

    @Test
    fun detects_palmdoc_signature() {
        val prefix = ByteArray(MOBI_SIGNATURE_OFFSET) + "TEXtREAd".toByteArray(Charsets.US_ASCII)
        assertThat(MagicBytes.sniff(prefix)).isEqualTo(FormatHint.MOBI)
    }

    @Test
    fun detects_fb2_after_xml_prologue() {
        assertThat(MagicBytes.sniff(FB2_PREFIX)).isEqualTo(FormatHint.FB2)
    }

    @Test
    fun detects_fb2_after_utf8_bom() {
        val prefix = "\u00EF\u00BB\u00BF".toByteArray(Charsets.ISO_8859_1) + FB2_PREFIX
        assertThat(MagicBytes.sniff(prefix)).isEqualTo(FormatHint.FB2)
    }

    @Test
    fun detects_rtf() {
        assertThat(MagicBytes.sniff("{\\rtf1\\ansi".toByteArray(Charsets.US_ASCII))).isEqualTo(FormatHint.RTF)
    }

    @Test
    fun detects_rar_as_cbr() {
        val prefix = "Rar!\u001A\u0007\u0000".toByteArray(Charsets.ISO_8859_1)
        assertThat(MagicBytes.sniff(prefix)).isEqualTo(FormatHint.CBR)
    }

    @Test
    fun detects_djvu_though_it_has_no_parser() {
        assertThat(MagicBytes.sniff("AT&TFORM".toByteArray(Charsets.US_ASCII))).isEqualTo(FormatHint.DJVU)
    }

    @Test
    fun plain_text_has_no_signature() {
        assertThat(MagicBytes.sniff("Просто текст без сигнатуры".toByteArray(Charsets.UTF_8))).isNull()
    }

    @Test
    fun empty_prefix_has_no_signature() {
        assertThat(MagicBytes.sniff(ByteArray(EMPTY_SIZE))).isNull()
    }

    private companion object {
        const val MOBI_SIGNATURE_OFFSET = 60
        const val EMPTY_SIZE = 0

        val FB2_PREFIX = (
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">"""
            ).toByteArray(Charsets.US_ASCII)
    }
}
