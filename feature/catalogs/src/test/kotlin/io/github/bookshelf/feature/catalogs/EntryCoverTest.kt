package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Источник обложки в карточке записи фида.
 *
 * Дефект, ради которого появился тест (найден на устройстве 28.09.2026): карточка передавала
 * в `BookCover` только `coverPath`, а у записей OPDS он всегда `null` — в списке книг вместо
 * обложек рисовалась заглушка с названием, хотя ссылка на обложку есть в фиде.
 */
class EntryCoverTest {

    @Test
    fun `local cover wins over the feed link`() {
        val cover = entryCoverOf(entry(coverPath = "/covers/1.webp", coverUrl = "https://host/1.jpg"))

        // Локальный файл важнее: он работает без сети и не тратит трафик.
        assertThat(cover.path).isEqualTo("/covers/1.webp")
        assertThat(cover.url).isNull()
    }

    @Test
    fun `feed link is used while the book is not downloaded`() {
        val cover = entryCoverOf(entry(coverPath = null, coverUrl = "https://host/covers/1.jpg"))

        assertThat(cover.path).isNull()
        assertThat(cover.url).isEqualTo("https://host/covers/1.jpg")
    }

    @Test
    fun `entry without any cover shows the placeholder`() {
        val cover = entryCoverOf(entry(coverPath = null, coverUrl = null))

        assertThat(cover.path).isNull()
        assertThat(cover.url).isNull()
    }

    private fun entry(coverPath: String?, coverUrl: String?): FeedEntryUi = FeedEntryUi(
        id = "entry-1",
        title = "Солярис",
        authors = "Станислав Лем",
        coverPath = coverPath,
        coverUrl = coverUrl,
    )
}

