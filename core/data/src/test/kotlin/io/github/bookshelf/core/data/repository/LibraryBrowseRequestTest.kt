package io.github.bookshelf.core.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Просьбы карточки книги к полке: «покажи книги этого автора / этой серии».
 *
 * Зачем отдельный тест: просьба живёт в общем состоянии сессии, и ошибка здесь не видна глазом —
 * неснятая просьба срабатывала бы при каждом возвращении на полку и затирала выбор пользователя,
 * а пустое имя открывало бы уровень «Автор: » без книг.
 */
class LibraryBrowseRequestTest {

    private val request = LibraryBrowseRequest()

    @Test
    fun `author request carries the name and is consumed once`() {
        request.openAuthor("Лев Толстой")

        assertThat(request.request.value).isEqualTo(LibraryBrowseRequest.Request.Author("Лев Толстой"))

        request.consume()

        // Снятая просьба не должна срабатывать второй раз.
        assertThat(request.request.value).isNull()
    }

    @Test
    fun `series request replaces the previous one`() {
        request.openAuthor("Лев Толстой")
        request.openSeries("Классика")

        assertThat(request.request.value).isEqualTo(LibraryBrowseRequest.Request.Series("Классика"))
    }

    @Test
    fun `blank names are ignored`() {
        request.openAuthor("Лев Толстой")

        request.openAuthor("   ")
        request.openSeries("")

        // Пустое имя означало бы уровень без книг: просьба просто не ставится.
        assertThat(request.request.value).isEqualTo(LibraryBrowseRequest.Request.Author("Лев Толстой"))
    }
}

