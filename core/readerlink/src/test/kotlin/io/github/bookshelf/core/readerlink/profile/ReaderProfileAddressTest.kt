package io.github.bookshelf.core.readerlink.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Кому нужна копия книги, а кому хватает нашего `content://`.
 *
 * Копия нужна FBReader: по ссылке SAF он книгу не открывает (дефект 05.10.2026). Остальным читалкам
 * копию не навязываем: они работают со ссылкой, а лишняя запись на диск и мусор в библиотеке
 * читалки никому не нужны.
 */
class ReaderProfileAddressTest {

    @Test
    fun `fbReader needs a copy`() {
        assertThat(profile(FbReaderPreset.PACKAGE_NAME).needsExportedCopy()).isTrue()
    }

    @Test
    fun `other readers keep our link`() {
        assertThat(profile("com.xrz.xreaderV3").needsExportedCopy()).isFalse()
        assertThat(profile("org.coolreader").needsExportedCopy()).isFalse()
        assertThat(profile("").needsExportedCopy()).isFalse()
    }

    private fun profile(packageName: String) = ReaderProfile(
        id = packageName,
        name = packageName,
        packageName = packageName,
        activityName = null,
        mimeTypes = emptyList(),
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = false,
    )
}
