package io.github.bookshelf.core.datastore.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Перевод лимитов настроек из мегабайт в байты (`SettingsUnits.kt`).
 *
 * Проверка существует потому, что лимитом пользуются два разных потребителя — уборка кэша
 * обложек и временная копия книги: ошибка в множителе (1000 вместо 1024) не падала бы нигде, а
 * просто тихо съедала диск.
 */
class SettingsUnitsTest {

    @Test
    fun `megabytes are converted with the binary multiplier`() {
        assertThat(megabytesToBytes(1)).isEqualTo(1024L * 1024L)
        assertThat(megabytesToBytes(100)).isEqualTo(104_857_600L)
        assertThat(megabytesToBytes(200)).isEqualTo(209_715_200L)
        assertThat(megabytesToBytes(500)).isEqualTo(524_288_000L)
    }

    @Test
    fun `zero limit means evict everything, not unlimited`() {
        assertThat(megabytesToBytes(0)).isEqualTo(0L)
    }

    @Test
    fun `negative setting is clamped to zero instead of going negative`() {
        // Отрицательный лимит в арифметике сравнивался бы как «всё помещается», и кэш рос бы без
        // границы — это хуже, чем честная уборка до нуля.
        assertThat(megabytesToBytes(-1)).isEqualTo(0L)
    }
}
