package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Подпись статуса чтения по имени значения («TDD» §8).
 *
 * Дефект, ради которого это появилось: `ReadingStatus.label` в `:core:data` отдавал русские слова
 * («не прочитано», «читаю», «прочитано»), и они уходили и в фасеты фильтров, и в карточку книги,
 * то есть оставались русскими в английском интерфейсе. Слой данных ресурсов не имеет — слова
 * выбирает интерфейс, а имя значения (`UNREAD`) остаётся кодом фильтра.
 */
class ReadingStatusTextTest {

    @Test
    fun `every status has its own text`() {
        assertThat(readingStatusText("UNREAD")).isEqualTo(AppText(R.string.status_unread))
        assertThat(readingStatusText("READING")).isEqualTo(AppText(R.string.status_reading))
        assertThat(readingStatusText("READ")).isEqualTo(AppText(R.string.status_read))
    }

    @Test
    fun `case does not matter`() {
        assertThat(readingStatusText(" reading ")).isEqualTo(AppText(R.string.status_reading))
    }

    @Test
    fun `the badge shows the percent only while reading`() {
        assertThat(readingStatusBadge("READING", 42))
            .isEqualTo(AppText(R.string.status_reading_progress, 42))
        // Процента нет — показываем просто «читаю», а не «читаю 0%».
        assertThat(readingStatusBadge("READING", null)).isEqualTo(AppText(R.string.status_reading))
        assertThat(readingStatusBadge("READ", 100)).isEqualTo(AppText(R.string.status_read))
    }

    @Test
    fun `an unknown status has no text`() {
        // Имя из будущей версии не должно ломать экран: подпись не найдётся, и покажем имя как есть.
        assertThat(readingStatusText("PAUSED")).isNull()
        assertThat(readingStatusText("")).isNull()
    }
}
