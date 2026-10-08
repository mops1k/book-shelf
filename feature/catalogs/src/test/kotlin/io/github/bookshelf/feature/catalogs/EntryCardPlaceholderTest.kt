package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тело карточки записи каталога: содержимое, загрузка или «запись не найдена».
 *
 * Дефект (сообщение Александра, 08.10.2026): пока карточка читала фид, экран показывал «Запись не
 * найдена» — пустое название выглядело как отсутствие записи, хотя запись ещё загружалась. Отсюда
 * правило: о ненайденной записи сообщаем только тогда, когда ничего не грузится.
 */
class EntryCardPlaceholderTest {

    @Test
    fun `a card that is still loading shows the loading row, not the missing state`() {
        assertThat(entryCardPlaceholder(isLoading = true, hasTitle = false))
            .isEqualTo(EntryCardPlaceholder.LOADING)
    }

    @Test
    fun `a loaded entry is shown`() {
        assertThat(entryCardPlaceholder(isLoading = false, hasTitle = true))
            .isEqualTo(EntryCardPlaceholder.CONTENT)
    }

    @Test
    fun `a missing entry is reported only when nothing is loading`() {
        assertThat(entryCardPlaceholder(isLoading = false, hasTitle = false))
            .isEqualTo(EntryCardPlaceholder.MISSING)
    }

    @Test
    fun `a refresh keeps the already shown entry`() {
        assertThat(entryCardPlaceholder(isLoading = true, hasTitle = true))
            .isEqualTo(EntryCardPlaceholder.CONTENT)
    }
}
