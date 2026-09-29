package io.github.bookshelf.core.data.repository.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подпись статуса чтения в фасете.
 *
 * Чипы статуса показывали код из базы («UNREAD»), хотя рядом авторы и жанры — названиями.
 * Хуже всего это выглядело в редакторе правил умной полки: готовые полки «Непрочитанные» и
 * «Читаю сейчас» открывались чипом из кода, по которому непонятно, что выбрано.
 */
class StatusLabelTest {

    @Test
    fun `status names are shown in human words`() {
        assertThat(statusLabel("UNREAD")).isEqualTo("не прочитано")
        assertThat(statusLabel("READING")).isEqualTo("читаю")
        assertThat(statusLabel("READ")).isEqualTo("прочитано")
    }

    @Test
    fun `unknown status stays as it is`() {
        // Неизвестное значение из старой версии базы не должно ронять экран и не должно
        // превращаться в пустую подпись чипа.
        assertThat(statusLabel("ЧИТАЮ")).isEqualTo("ЧИТАЮ")
    }
}
