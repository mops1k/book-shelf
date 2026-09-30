package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Приведение адреса читалки к нашему адресу книги.
 *
 * Повод: прогресс чтения не подхватывался для книг в архиве — в журнале `BookUnknown`. Оказалось,
 * читалка называет такую книгу адресом **внутри архива** (`zip-entry://`), где наш `content://`
 * закодирован ещё раз, а следом дописан файл внутри архива. Строки ниже — настоящие: путь взят из
 * журнала устройства (`logcat -s BookShelf:W`), наш адрес — из `books.documentUri` на том же
 * устройстве (Bigme HiBreak, 30.09.2026, книга «Книга Уткина Мы тут жизнь живём [litres].fb2.zip»).
 *
 * Проверяются границы: обычный адрес (не архив) не трогаем вовсе — иначе декодирование испортило бы
 * его собственные `%XX`; мусор без `/document/` возвращается как есть, а не падает.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ReaderBookPathTest {

    @Test
    fun `a book inside an archive is brought back to our document address`() {
        val normalized = ReaderBookPath.toDocumentUri(fixture("zip-entry-path.txt"))

        assertThat(normalized).isEqualTo(fixture("document-uri.txt"))
    }

    @Test
    fun `a plain document address is left untouched`() {
        // Для книги без архива читалка отдаёт ровно наш адрес (проверено на устройстве 29.09.2026).
        val normalized = ReaderBookPath.toDocumentUri(fixture("document-uri.txt"))

        assertThat(normalized).isEqualTo(fixture("document-uri.txt"))
    }

    @Test
    fun `an address without a document segment is returned as is`() {
        val garbage = "zip-entry://content%253A%252F%252Fnothing-here"

        // Ничего не сломалось и ничего не выдумано: сравнение просто не найдёт книгу.
        assertThat(ReaderBookPath.toDocumentUri(garbage)).isEqualTo(garbage)
    }

    /**
     * Настоящие строки с устройства (Bigme HiBreak, 30.09.2026): путь, которым читалка назвала книгу
     * в архиве, и наш `books.documentUri` для той же книги. Держим их файлами, а не константами в
     * коде: они длиннее допустимой длины строки, а подделывать их нельзя — на них и держится фикс.
     */
    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/reader-paths/$name"),
    ) { "нет фикстуры $name" }.bufferedReader().use { reader -> reader.readText() }.trim()
}
