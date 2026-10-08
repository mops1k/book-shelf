package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Записи фидов, которые пользователь уже видел в браузере каталога.
 *
 * Дефект, ради которого это проверяется (сообщение Александра, 08.10.2026): «Новинки → Все новинки
 * → Боевая целительница» — книга видна в списке, а карточка отвечает «Запись больше не входит в
 * этот фид». Карточка перечитывала первую страницу фида, тогда как книга пришла со второй. Поэтому
 * хранилище обязано держать и саму запись, и адрес страницы, откуда она пришла.
 */
class LoadedFeedEntriesTest {

    private val entries = LoadedFeedEntries()

    @Test
    fun `an entry shown by the list is found by catalog and id`() {
        entries.remember(CATALOG_ID, listOf(entry(id = "2", title = "Боевая целительница")), PAGE_URL)

        assertThat(entries.find(CATALOG_ID, "2")?.title).isEqualTo("Боевая целительница")
    }

    @Test
    fun `an entry keeps the address of the page it came from`() {
        entries.remember(CATALOG_ID, listOf(entry(id = "2", title = "Боевая целительница")), PAGE_URL)

        assertThat(entries.feedUrlOf(CATALOG_ID, "2")).isEqualTo(PAGE_URL)
    }

    @Test
    fun `root feed entries have no page address of their own`() {
        entries.remember(CATALOG_ID, listOf(entry(id = "1", title = "Черновик")), feedUrl = null)

        assertThat(entries.find(CATALOG_ID, "1")).isNotNull()
        // null означает «корневой фид каталога»: карточка должна читать именно его.
        assertThat(entries.feedUrlOf(CATALOG_ID, "1")).isNull()
    }

    @Test
    fun `another catalog does not hand the entry over`() {
        entries.remember(CATALOG_ID, listOf(entry(id = "2", title = "Боевая целительница")), PAGE_URL)

        assertThat(entries.find("another-catalog", "2")).isNull()
    }

    @Test
    fun `the address of the page the entry first came from is kept`() {
        entries.remember(CATALOG_ID, listOf(entry(id = "2", title = "Боевая целительница")), PAGE_URL)
        entries.remember(
            CATALOG_ID,
            listOf(entry(id = "2", title = "Боевая целительница (обновлена)")),
            ANOTHER_URL,
        )

        // Догрузка страниц не переписывает адрес записи адресом первой страницы: иначе карточка
        // книги со второй страницы снова перечитывала бы первую.
        assertThat(entries.feedUrlOf(CATALOG_ID, "2")).isEqualTo(PAGE_URL)
        // Сама запись обновляется: фид мог прислать новые метаданные.
        assertThat(entries.find(CATALOG_ID, "2")?.title).isEqualTo("Боевая целительница (обновлена)")
    }

    @Test
    fun `an entry nobody showed is not found`() {
        assertThat(entries.find(CATALOG_ID, "нет такой")).isNull()
        assertThat(entries.feedUrlOf(CATALOG_ID, "нет такой")).isNull()
    }

    private companion object {
        const val CATALOG_ID = "c1"
        const val PAGE_URL = "https://host/opds/new/1/new/"
        const val ANOTHER_URL = "https://host/opds/new/2/new/"
    }
}
