package io.github.bookshelf.core.readerlink.progress

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.readerlink.koreader.KoReaderPreset
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset
import org.junit.Test

/**
 * Какая читалка какой источник прогресса включает (`progressSourceOf`).
 *
 * Связь нужна для правила нагрузки: пока идёт чтение, разрешено спрашивать **только ту** читалку,
 * которая открыта сейчас. Книга у нас есть (её сохранила сессия), а читалка известна по профилю,
 * которым её открывали, — поэтому профиль обязан отображаться в источник.
 *
 * Ошибка здесь тихая с двух сторон: неверное отображение читает чужое состояние (мы этого как раз
 * избегаем), а отсутствующее — оставляет книгу без прогресса, хотя человек читал.
 */
class ProgressSourceOfProfileTest {

    @Test
    fun `the fbReader profile maps to the aidl source`() {
        assertThat(progressSourceOf(FbReaderPreset.ID)).isEqualTo(ProgressSourceId.FBREADER_AIDL)
    }

    @Test
    fun `the koReader profile maps to the sidecar source`() {
        assertThat(progressSourceOf(KoReaderPreset.ID)).isEqualTo(ProgressSourceId.KOREADER_SIDECAR)
    }

    @Test
    fun `a reader we have no source for maps to nothing`() {
        // Moon+ Reader, ReadEra, eBoox: прогресса у нас для них нет, и «опросить всех» вместо
        // них нельзя — это и есть та нагрузка, от которой отказались.
        assertThat(progressSourceOf("moonreader")).isNull()
        assertThat(progressSourceOf("")).isNull()
    }
}
