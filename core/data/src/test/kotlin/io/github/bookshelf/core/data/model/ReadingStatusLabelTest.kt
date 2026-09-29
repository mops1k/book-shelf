package io.github.bookshelf.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подписи статуса чтения.
 *
 * Бейдж на обложке — единственное место на полке, где видно состояние книги, поэтому подпись
 * проверяется отдельно: код `UNREAD` и выдуманный «читаю 0%» одинаково вводят в заблуждение.
 */
class ReadingStatusLabelTest {

    @Test
    fun `status names are shown in human words`() {
        assertThat(ReadingStatus.UNREAD.label).isEqualTo("не прочитано")
        assertThat(ReadingStatus.READING.label).isEqualTo("читаю")
        assertThat(ReadingStatus.READ.label).isEqualTo("прочитано")
    }

    @Test
    fun `badge adds the progress only when the reader reported it`() {
        assertThat(ReadingStatus.READING.badge(42)).isEqualTo("читаю 42%")
        // Читалка прогресс не отдаёт — процент не выдумывается.
        assertThat(ReadingStatus.READING.badge(null)).isEqualTo("читаю")
        // Для прочитанных и непрочитанных процент не показывается: он ничего не добавляет.
        assertThat(ReadingStatus.READ.badge(100)).isEqualTo("прочитано")
        assertThat(ReadingStatus.UNREAD.badge(0)).isEqualTo("не прочитано")
    }
}

