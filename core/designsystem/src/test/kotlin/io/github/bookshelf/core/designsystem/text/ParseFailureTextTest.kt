package io.github.bookshelf.core.designsystem.text

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.ParseFailure
import io.github.bookshelf.core.designsystem.R
import org.junit.Test

/**
 * Текст отказа разбора выбирается по коду (docs/tdd.md §8).
 *
 * Дефект, ради которого это появилось: причина отказа парсера хранилась в базе готовой русской
 * строкой и показывалась в карточке — в английском интерфейсе она оставалась русской. Теперь в
 * базе код, а слова выбирает экран; здесь проверяется, что у каждого кода свой текст.
 */
class ParseFailureTextTest {

    @Test
    fun `every failure code has its own text`() {
        assertThat(ParseFailure.FORMAT_UNSUPPORTED.text())
            .isEqualTo(AppText(R.string.parse_failure_format_unsupported))
        assertThat(ParseFailure.CORRUPT_FILE.text())
            .isEqualTo(AppText(R.string.parse_failure_corrupt_file))
        assertThat(ParseFailure.EMPTY_FILE.text())
            .isEqualTo(AppText(R.string.parse_failure_empty_file))
        assertThat(ParseFailure.ARCHIVE_LIMIT.text())
            .isEqualTo(AppText(R.string.parse_failure_archive_limit))
        assertThat(ParseFailure.UNKNOWN.text())
            .isEqualTo(AppText(R.string.parse_failure_unknown))
    }
}
