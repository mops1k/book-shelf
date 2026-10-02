package io.github.bookshelf.core.readerlink.discovery

import android.content.Intent
import android.content.IntentFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Кто НЕ читалка — по объявленным типам и по чёрному списку известных не-читалок.
 *
 * Требование Александра (01.10.2026): «фото, дик, браузеры, html надо добавить в исключения и не
 * выводить их в списке, а также приложения редактирования документов», и уточнение — «надо вести
 * не белый список читалок, а чёрный список не читалок, которые также поддерживают открытие книг».
 *
 * Белый список читалок уже был и оказался хуже болезни: с ним пропадал XReader. Поэтому правило
 * из двух частей:
 * * ЧТО ОБЪЯВЛЕНО. Приложение, поймавшее книжный тип общим шаблоном (звёздочка) или текстовым
 *   типом, читалкой не становится: так выглядят браузер, «Фото» и просмотрщик HTML (замер
 *   с устройства).
 * * ЧЁРНЫЙ СПИСОК. Словарь (на устройстве — `com.eusoft.eudic`) объявляет `application/epub+zip`
 *   честно, но читалкой не является: такие пакеты исключаются по имени.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class NonReaderAppsTest {

    @Test
    fun `a concrete book mime is evidence of a reader`() {
        val filter = filterOf("*", "application/epub+zip", "application/pdf")

        assertThat(declaredBookMimeTypes(filter)).containsExactly("application/epub+zip")
    }

    @Test
    fun `wildcards and non-book formats are not evidence`() {
        // Так выглядят Brave, «Фото» и просмотрщик HTML.
        assertThat(declaredBookMimeTypes(filterOf("*"))).isEmpty()
        assertThat(declaredBookMimeTypes(filterOf("text/html", "text/plain", "application/pdf")))
            .isEmpty()
        assertThat(declaredBookMimeTypes(filterOf("image/*", "audio/*", "application/xhtml+xml")))
            .isEmpty()
    }

    @Test
    fun `a dictionary that opens epub is still not a reader`() {
        // Пакет с устройства: словарь объявляет epub, но читалкой не является.
        assertThat(readerRankOf(EUDIC, listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.OTHER)
    }

    @Test
    fun `browsers, galleries and viewers are excluded by package`() {
        assertThat(readerRankOf("com.brave.browser", listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.OTHER)
        assertThat(readerRankOf("com.google.android.apps.photos", listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.OTHER)
        assertThat(readerRankOf("com.android.htmlviewer", listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.OTHER)
        assertThat(readerRankOf("com.microsoft.office.word", listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.OTHER)
    }

    @Test
    fun `a speech engine that opens books is not a reader`() {
        assertThat(readerRankOf("com.CodeBySonu.VoxSherpa", listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.OTHER)
    }

    @Test
    fun `a real reader is not excluded even if it declares wildcards`() {
        // XReader: объявляет и `*`, и книжные форматы — важно второе.
        assertThat(
            readerRankOf(
                X_READER,
                listOf("application/epub+zip", "application/fb2", "application/vnd.comicbook-rar"),
            ),
        ).isEqualTo(ReaderRank.BOOK_FORMAT)
    }

    @Test
    fun `a known reader stays first even without declared formats`() {
        assertThat(readerRankOf("com.fbreader", emptyList())).isEqualTo(ReaderRank.KNOWN)
    }

    @Test
    fun `all declared types are kept, not only book ones`() {
        // Так выглядит FBReader: epub и pdf объявлены. Для решения «читалка ли это» важно epub,
        // а для списка форматов — оба: иначе PDF некуда было бы привязать.
        val fbReader = filterOf("*", "application/epub+zip", "application/pdf")

        assertThat(declaredBookMimeTypes(fbReader)).containsExactly("application/epub+zip")
        assertThat(declaredMimeTypes(fbReader))
            .containsExactly("application/epub+zip", "application/pdf")
    }

    @Test
    fun `a null filter gives no evidence`() {
        assertThat(declaredBookMimeTypes(null)).isEmpty()
    }

    private fun filterOf(vararg types: String) = IntentFilter().apply {
        addAction(Intent.ACTION_VIEW)
        types.forEach { type ->
            // В манифесте пишут , внутри Android это уже  + слэш + : другой вид
            //  отвергает как некорректный MIME.
            addDataType(if (type == "*") "*/*" else type)
        }
    }

    private companion object {
        const val X_READER = "com.xrz.xreaderV3"
        const val EUDIC = "com.eusoft.eudic"
    }
}
