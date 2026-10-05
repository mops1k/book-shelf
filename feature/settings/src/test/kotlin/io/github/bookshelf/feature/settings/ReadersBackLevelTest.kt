package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.readerlink.mime.ReaderFormatCategory
import org.junit.Test

/**
 * Куда ведёт кнопка «Назад» в шапке экрана «Читалки».
 *
 * Замечание Александра (01.10.2026): «ты продублировал кнопку назад вместо того, чтобы в шапке
 * переиспользовать». Кнопка в шапке одна, и на трёхуровневом экране она поднимает на уровень выше,
 * а с первого уровня — выходит из раздела.
 */
class ReadersBackLevelTest {

    @Test
    fun `from the list of categories the header leaves the section`() {
        assertThat(readersBackLevelOf(ReadersLevelUi.Categories)).isNull()
    }

    @Test
    fun `from formats the header goes back to the categories`() {
        assertThat(readersBackLevelOf(ReadersLevelUi.Formats(ReaderFormatCategory.BOOKS)))
            .isEqualTo(ReadersLevelUi.Categories)
    }

    @Test
    fun `from readers of a format the header goes back to the formats of that category`() {
        // Не в первую категорию, а именно в ту, откуда человек пришёл: иначе «Манги» → CBR →
        // назад привели бы в «Книги».
        assertThat(readersBackLevelOf(ReadersLevelUi.ReadersOfFormat("cbr")))
            .isEqualTo(ReadersLevelUi.Formats(ReaderFormatCategory.MANGA))
        assertThat(readersBackLevelOf(ReadersLevelUi.ReadersOfFormat("epub")))
            .isEqualTo(ReadersLevelUi.Formats(ReaderFormatCategory.BOOKS))
    }

    @Test
    fun `from the default readers sub-screen the header goes back to the categories`() {
        // Список читалок переехал на отдельный экран (требование Александра 05.10.2026), и «Назад»
        // с него возвращает на первый уровень, а не выходит из раздела.
        assertThat(readersBackLevelOf(ReadersLevelUi.Defaults)).isEqualTo(ReadersLevelUi.Categories)
    }

    @Test
    fun `from the progress sub-screen the header goes back to the categories`() {
        // Подэкран «Прогресс чтения» открывается с первого уровня и туда же возвращает (требование
        // Александра 05.10.2026: вынести блоки «Прогресс из …» с общего экрана «Читалки»).
        assertThat(readersBackLevelOf(ReadersLevelUi.Progress)).isEqualTo(ReadersLevelUi.Categories)
    }
}
