package io.github.bookshelf.feature.bookdetails

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подпись пути к файлу в карточке книги.
 *
 * Дефект, ради которого появился тест: строка «Путь» была пустой у всех книг, потому что
 * состояние карточки объявляло поле, а маппинг его не заполнял (вопрос Александра 28.09.2026).
 */
class FilePathLabelTest {

    @Test
    fun `folder and path inside it are shown together`() {
        assertThat(filePathLabel("Books", "Лем/Солярис.fb2")).isEqualTo("Books / Лем/Солярис.fb2")
    }

    @Test
    fun `path alone is shown when the folder name is unknown`() {
        // Имя папки приходит отдельной подпиской: до неё путь всё равно должен быть виден.
        assertThat(filePathLabel(null, "Солярис.fb2")).isEqualTo("Солярис.fb2")
        assertThat(filePathLabel("  ", "Солярис.fb2")).isEqualTo("Солярис.fb2")
    }

    @Test
    fun `empty path yields only the folder`() {
        assertThat(filePathLabel("Books", "")).isEqualTo("Books")
        assertThat(filePathLabel(null, "")).isEmpty()
    }
}

