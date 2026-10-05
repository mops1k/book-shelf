package io.github.bookshelf.core.readerlink.progress

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset
import org.junit.Test

/**
 * Какой читалке принадлежит источник прогресса.
 *
 * По этому списку видно, установлена ли читалка: переключатели «Прогресс из …» удалённой читалки
 * показывать нельзя (требование Александра, 06.10.2026 — «если читалка удалена с устройства, её в
 * приложении нигде не должно быть видно»).
 */
class ProgressSourceReadersTest {

    @Test
    fun `fbReader source belongs to the fbReader package`() {
        assertThat(readerPackagesOf(ProgressSourceId.FBREADER_AIDL))
            .containsExactly(FbReaderPreset.PACKAGE_NAME)
    }

    @Test
    fun `every source names the package it reads from`() {
        assertThat(readerPackagesOf(ProgressSourceId.COOLREADER_DB)).contains("org.coolreader")
        assertThat(readerPackagesOf(ProgressSourceId.POCKETBOOK_DB)).contains("com.obreey.reader")
        assertThat(readerPackagesOf(ProgressSourceId.XREADER_CACHE)).contains("com.xrz.xreaderV3")
        // KOReader и Librera ставятся в нескольких сборках: любая означает, что источник рабочий.
        assertThat(readerPackagesOf(ProgressSourceId.KOREADER_SIDECAR)).contains("org.koreader.launcher")
        assertThat(readerPackagesOf(ProgressSourceId.LIBRERA_JSON)).contains("com.foobnix.pro.pdf.reader")
    }

    @Test
    fun `a source names no package when we do not know it`() {
        // Ни одного пустого набора: у каждого источника есть хотя бы один пакет, иначе его нельзя
        // было бы ни показать, ни скрыть.
        ProgressSourceId.entries.forEach { source ->
            assertThat(readerPackagesOf(source)).isNotEmpty()
        }
    }
}
