package io.github.bookshelf.work

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Выбор обложки после разбора метаданных.
 *
 * Проверяется главное свойство кэша обложек: файл книги не меняется, поэтому повторный разбор
 * ради названия и жанров **не перезаписывает** уже сохранённую картинку. Заново она берётся
 * только по явной команде или когда скан сбросил кэш (тогда сохранённой обложки нет).
 */
class CoverReuseTest {

    @Test
    fun `re-parsing metadata keeps the cached cover`() = runTest {
        var stored = 0

        val path = chooseCoverPath(
            cachedCover = CACHED,
            hasExtractedCover = true,
            refreshCover = false,
            store = { stored += 1; NEW },
        )

        assertThat(path).isEqualTo(CACHED)
        assertThat(stored).isEqualTo(0)
    }

    @Test
    fun `explicit refresh stores the extracted cover`() = runTest {
        val path = chooseCoverPath(
            cachedCover = CACHED,
            hasExtractedCover = true,
            refreshCover = true,
            store = { NEW },
        )

        assertThat(path).isEqualTo(NEW)
    }

    @Test
    fun `first parse without cache stores the cover`() = runTest {
        val path = chooseCoverPath(
            cachedCover = null,
            hasExtractedCover = true,
            refreshCover = false,
            store = { NEW },
        )

        assertThat(path).isEqualTo(NEW)
    }

    @Test
    fun `refresh without an extracted cover does not lose the cached one`() = runTest {
        // PDF без обложки или частичный разбор: `null` записался бы как «стереть обложку».
        val path = chooseCoverPath(
            cachedCover = CACHED,
            hasExtractedCover = false,
            refreshCover = true,
            store = { error("обложку не извлекали — записывать нечего") },
        )

        assertThat(path).isEqualTo(CACHED)
    }

    @Test
    fun `failed cover write falls back to the cached path`() = runTest {
        val path = chooseCoverPath(
            cachedCover = CACHED,
            hasExtractedCover = true,
            refreshCover = true,
            store = { null },
        )

        assertThat(path).isEqualTo(CACHED)
    }

    private companion object {
        const val CACHED = "/data/covers/doc-1.webp"
        const val NEW = "/data/covers/doc-1-new.webp"
    }
}

