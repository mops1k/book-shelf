package io.github.bookshelf.core.opds.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Путь скачанного файла по шаблону из настроек (`DownloadFileName.kt`).
 *
 * Проверяется то, чего раньше не было вовсе: в имя попадал только название, а автор терялся.
 * Теперь шаблон задаёт ещё и подпапки, поэтому важны обе крайности — корректная раскладка
 * «Автор/Серия/Название» и то, что пустые значения не превращаются в папку без имени.
 */
class DownloadFileNameTest {

    @Test
    fun `default template puts the author before the title`() {
        val path = renderDownloadRelativePath(
            template = DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE,
            title = "Солярис",
            authors = listOf("Станислав Лем", "Другой Автор"),
            series = null,
            extension = "fb2",
        )

        // Берётся первый автор: несколько авторов в имени файла превратились бы в список.
        assertThat(path).isEqualTo("Станислав Лем - Солярис.fb2")
    }

    @Test
    fun `slash in the template makes a folder`() {
        val path = renderDownloadRelativePath(
            template = "{author}/{series}/{title}.{ext}",
            title = "Солярис",
            authors = listOf("Станислав Лем"),
            series = "Пилот",
            extension = "fb2",
        )

        assertThat(path).isEqualTo("Станислав Лем/Пилот/Солярис.fb2")
    }

    @Test
    fun `missing series drops the folder instead of creating an unnamed one`() {
        val path = renderDownloadRelativePath(
            template = "{author}/{series}/{title}.{ext}",
            title = "Солярис",
            authors = listOf("Станислав Лем"),
            series = null,
            extension = "fb2",
        )

        // Иначе получилась бы папка с пустым именем или «/Солярис.fb2» — путь, который провайдер
        // либо отвергнет, либо создаст там, где пользователь не ожидал.
        assertThat(path).isEqualTo("Станислав Лем/Солярис.fb2")
    }

    @Test
    fun `missing author drops the leading separator`() {
        val path = renderDownloadRelativePath(
            template = DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE,
            title = "Анонимное сочинение",
            authors = emptyList(),
            series = null,
            extension = "fb2",
        )

        assertThat(path).isEqualTo("Анонимное сочинение.fb2")
    }

    @Test
    fun `forbidden characters are removed but cyrillic survives`() {
        val path = renderDownloadRelativePath(
            template = "{author}/{title}.{ext}",
            title = "Полдень XXI век 2002 №3: история?",
            authors = listOf("Редакция"),
            series = null,
            extension = "fb2",
        )

        // Двоеточие и вопросительный знак ломают путь у части провайдеров, а «№» и кириллица —
        // обычные символы названия и обязаны сохраниться.
        assertThat(path).isEqualTo("Редакция/Полдень XXI век 2002 №3 история.fb2")
    }

    @Test
    fun `extension is appended when the template has no placeholder for it`() {
        val path = renderDownloadRelativePath(
            template = "{author}/{title}",
            title = "Солярис",
            authors = listOf("Станислав Лем"),
            series = null,
            extension = "fb2.zip",
        )

        // По расширению приложение выбирает парсер: имя без него сделало бы книгу нечитаемой.
        assertThat(path).isEqualTo("Станислав Лем/Солярис.fb2.zip")
    }

    @Test
    fun `path is never empty even without a title`() {
        val path = renderDownloadRelativePath(
            template = "{title}.{ext}",
            title = "",
            authors = emptyList(),
            series = null,
            extension = "fb2",
        )

        assertThat(path).isEqualTo("book.fb2")
    }

    @Test
    fun `slash inside a title does not create a folder`() {
        val path = renderDownloadRelativePath(
            template = "{title}.{ext}",
            title = "Марсианские хроники / Солярис",
            authors = emptyList(),
            series = null,
            extension = "fb2",
        )

        // Слэш приходит из названия книги, а не из шаблона: подпапка из названия — не замысел
        // пользователя, и файл должен остаться в корне выбранной папки. Двойной пробел, оставшийся
        // от разделителя, схлопывается.
        assertThat(path).isEqualTo("Марсианские хроники Солярис.fb2")
    }
}
