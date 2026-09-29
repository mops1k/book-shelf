package io.github.bookshelf.core.opds.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Имя серии из подписи ссылки на неё.
 *
 * Флибуста называет ссылку «Все книги серии "Ксили"». Показать это в карточке книги значит
 * показать служебную фразу вместо названия серии, поэтому имя достаётся из подписи.
 */
class OpdsSeriesNameTest {

    @Test
    fun `flibusta link title becomes just the series name`() {
        assertThat(seriesNameFromLinkTitle("Все книги серии \"Ксили\"")).isEqualTo("Ксили")
    }

    @Test
    fun `guillemets are understood too`() {
        assertThat(seriesNameFromLinkTitle("Все книги серии «Ксили»")).isEqualTo("Ксили")
    }

    @Test
    fun `unclosed quote still gives the name`() {
        // Битую подпись лучше показать целиком, чем потерять название серии.
        assertThat(seriesNameFromLinkTitle("Все книги серии \"Ксили")).isEqualTo("Ксили")
    }

    @Test
    fun `plain title without a prefix is kept as is`() {
        assertThat(seriesNameFromLinkTitle("Ксили")).isEqualTo("Ксили")
    }

    @Test
    fun `blank title means no series name`() {
        assertThat(seriesNameFromLinkTitle(null)).isNull()
        assertThat(seriesNameFromLinkTitle("   ")).isNull()
    }
}
