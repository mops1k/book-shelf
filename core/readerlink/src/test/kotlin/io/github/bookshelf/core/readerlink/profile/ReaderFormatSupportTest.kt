package io.github.bookshelf.core.readerlink.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Поддерживает ли читалка формат — по объявленным MIME-типам, а не по догадке.
 *
 * Нужно экрану «Читалки»: человек выбирает, чем открывать FB2, EPUB, PDF и остальное, и в списке
 * должны быть только те читалки, которые этот формат действительно умеют. Ошибиться здесь дорого
 * в обе стороны: лишняя читалка в списке даст отказ при открытии, а недостающая — не даст выбрать
 * ту, которой человек пользуется.
 */
class ReaderFormatSupportTest {

    @Test
    fun `a reader that declares epub supports epub`() {
        assertThat(reader("application/epub+zip").supportsExtension("epub")).isTrue()
    }

    @Test
    fun `fb2 counts when the reader declares any of its names`() {
        // Так объявляет FBReader с устройства: `application/fb2` и `application/x-fictionbook+xml`.
        assertThat(reader("application/fb2").supportsExtension("fb2")).isTrue()
        assertThat(reader("application/x-fictionbook+xml").supportsExtension("fb2")).isTrue()
        assertThat(reader("application/x-fb2").supportsExtension("fb2")).isTrue()
    }

    @Test
    fun `a format the reader never declared is not supported`() {
        assertThat(reader("application/epub+zip").supportsExtension("mobi")).isFalse()
    }

    @Test
    fun `the octet-stream probe does not count as support`() {
        // `application/octet-stream` отвечает ЛЮБОЕ приложение: считать это поддержкой формата
        // значило бы предложить для FB2 и галерею, и словарь.
        assertThat(reader("application/octet-stream").supportsExtension("fb2")).isFalse()
    }

    @Test
    fun `mobi and its siblings share one mime`() {
        val kindle = reader("application/x-mobipocket-ebook")

        assertThat(kindle.supportsExtension("mobi")).isTrue()
        assertThat(kindle.supportsExtension("azw3")).isTrue()
        assertThat(kindle.supportsExtension("prc")).isTrue()
    }

    @Test
    fun `alternative names of the same format count`() {
        // Так объявляют себя читалки с устройства: FBReader пишет `x-cbz`/`x-cbr`, XReader —
        // `vnd.comicbook-*`. Оба обязаны попасть в список для манги, иначе выбор читалки
        // для манги окажется мнимым.
        assertThat(reader("application/x-cbz").supportsExtension("cbz")).isTrue()
        assertThat(reader("application/x-cbr").supportsExtension("cbr")).isTrue()
        assertThat(reader("application/vnd.comicbook-rar").supportsExtension("cbr")).isTrue()
        assertThat(reader("application/epub").supportsExtension("epub")).isTrue()
        assertThat(reader("application/x-djvu").supportsExtension("djvu")).isTrue()
        assertThat(reader("application/vnd.amazon.mobi8-ebook").supportsExtension("azw3")).isTrue()
    }

    @Test
    fun `comic archives are told apart from epub`() {
        val comics = reader("application/vnd.comicbook+zip")

        assertThat(comics.supportsExtension("cbz")).isTrue()
        assertThat(comics.supportsExtension("epub")).isFalse()
    }

    @Test
    fun `the formats a reader supports are listed by extension`() {
        val reader = reader("application/epub+zip", "application/x-mobipocket-ebook", "application/fb2")

        assertThat(reader.supportedExtensions()).containsAtLeast("epub", "mobi", "azw3", "prc", "fb2")
        assertThat(reader.supportedExtensions()).doesNotContain("cbz")
    }

    private fun reader(vararg mimeTypes: String) = ReaderProfile(
        id = "reader",
        name = "Reader",
        packageName = "com.example.reader",
        activityName = null,
        mimeTypes = mimeTypes.toList(),
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = false,
    )
}
