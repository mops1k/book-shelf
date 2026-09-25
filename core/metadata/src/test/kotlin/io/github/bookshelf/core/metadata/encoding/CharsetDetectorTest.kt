package io.github.bookshelf.core.metadata.encoding

import com.google.common.truth.Truth.assertWithMessage
import java.nio.charset.Charset
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Определение кодировки — чистые байтовые правила, поэтому проверяется обычным JUnit.
 * Набор кейсов повторяет обязательные фикстуры из docs/formats.md §10
 * (utf-8 с BOM, utf-16, windows-1251 без BOM, объявленный koi8-r).
 */
@RunWith(Parameterized::class)
class CharsetDetectorTest(
    private val caseName: String,
    private val prefix: ByteArray,
    private val declared: String?,
    private val expected: String,
) {

    private val detector = DefaultCharsetDetector()

    @Test
    fun detects_expected_charset() {
        assertWithMessage(caseName).that(detector.detect(prefix, declared)).isEqualTo(expected)
    }

    companion object {

        private const val CYRILLIC_SAMPLE = "Привет, мир"
        private const val XML_KOI8_DECLARATION =
            """<?xml version="1.0" encoding="koi8-r"?><FictionBook/>"""

        private val WINDOWS_1251: Charset = Charset.forName("windows-1251")
        private val BOM_UTF8 = "\u00EF\u00BB\u00BF".toByteArray(Charsets.ISO_8859_1)
        private val BOM_UTF16_LE = "\u00FF\u00FE".toByteArray(Charsets.ISO_8859_1)
        private val BOM_UTF16_BE = "\u00FE\u00FF".toByteArray(Charsets.ISO_8859_1)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> = listOf(
            arrayOf<Any?>(
                "utf-8 with BOM",
                BOM_UTF8 + CYRILLIC_SAMPLE.toByteArray(Charsets.UTF_8),
                null,
                "UTF-8",
            ),
            arrayOf<Any?>(
                "utf-16le with BOM",
                BOM_UTF16_LE + CYRILLIC_SAMPLE.toByteArray(Charsets.UTF_16LE),
                null,
                "UTF-16LE",
            ),
            arrayOf<Any?>(
                "utf-16be with BOM",
                BOM_UTF16_BE + CYRILLIC_SAMPLE.toByteArray(Charsets.UTF_16BE),
                null,
                "UTF-16BE",
            ),
            arrayOf<Any?>(
                "windows-1251 without BOM",
                CYRILLIC_SAMPLE.toByteArray(WINDOWS_1251),
                null,
                "windows-1251",
            ),
            arrayOf<Any?>(
                "koi8-r from xml declaration",
                XML_KOI8_DECLARATION.toByteArray(Charsets.US_ASCII),
                null,
                "KOI8-R",
            ),
            arrayOf<Any?>(
                "declared cp1251 is canonized",
                CYRILLIC_SAMPLE.toByteArray(WINDOWS_1251),
                "cp1251",
                "windows-1251",
            ),
            arrayOf<Any?>(
                "plain ascii stays utf-8",
                "plain text".toByteArray(Charsets.US_ASCII),
                null,
                "UTF-8",
            ),
        )
    }
}
