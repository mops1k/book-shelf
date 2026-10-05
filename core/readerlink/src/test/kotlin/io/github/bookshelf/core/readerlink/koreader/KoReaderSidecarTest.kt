package io.github.bookshelf.core.readerlink.koreader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Путь sidecar-файла KOReader рядом с книгой (`KoReaderSidecar`).
 *
 * Правило взято из исходников KOReader (`frontend/docsettings.lua`), а не выведено из наблюдений:
 * * папка — путь книги **без последнего расширения** плюс `.sdr`
 *   (`path:match("(.*)%.") or path`);
 * * файл — `metadata.<последнее расширение>.lua` (`getSidecarFilename`), а если расширения нет, то
 *   `metadata._.lua`.
 *
 * Ошибиться здесь дёшево и незаметно: неверный путь означает «прогресса нет», и это выглядит как
 * «человек не читал», а не как дефект. Поэтому проверяются ровно те случаи, которые встречаются в
 * библиотеке: обычная книга, книга в архиве (`fb2.zip`) и файл без расширения.
 *
 * Строка sidecar проверена на живом устройстве (Bigme HiBreak, 05.10.2026): для
 * `Zlotnikov_…_1_Vselennaya-neudachnikov.563756.fb2.epub` KOReader создал
 * `…563756.fb2.sdr/metadata.epub.lua`.
 */
class KoReaderSidecarTest {

    @Test
    fun `a book gives its sibling metadata file`() {
        val sibling = KoReaderSidecar.siblingOf("Zlotnikov_Vselennaya-neudachnikov.fb2.epub")

        assertThat(sibling).isEqualTo("Zlotnikov_Vselennaya-neudachnikov.fb2.sdr/metadata.epub.lua")
    }

    @Test
    fun `a book inside an archive keeps the archive extension in the folder and in the file`() {
        // Для `Книга.fb2.zip` KOReader строит папку `Книга.fb2.sdr`, а файл — `metadata.zip.lua`:
        // расширением считается последнее (`zip`), а в имени папки последнее расширение срезано.
        val sibling = KoReaderSidecar.siblingOf("Книга Уткина.fb2.zip")

        assertThat(sibling).isEqualTo("Книга Уткина.fb2.sdr/metadata.zip.lua")
    }

    @Test
    fun `a file without extension gets the underscore name`() {
        // `path:match(".*%.(.+)")` для имени без точки ничего не находит, и KOReader берёт `_`.
        val sibling = KoReaderSidecar.siblingOf("notes")

        assertThat(sibling).isEqualTo("notes.sdr/metadata._.lua")
    }

    @Test
    fun `only the file name is used, never the folders`() {
        // Путь книги в ref относительный и уже без папки, но защититься стоит: если сюда попадёт
        // путь целиком, sidecar уедет в чужую папку.
        val sibling = KoReaderSidecar.siblingOf("Лем/Солярис.epub")

        assertThat(sibling).isEqualTo("Солярис.sdr/metadata.epub.lua")
    }

    @Test
    fun `a dotfile without a real extension is still handled`() {
        val sibling = KoReaderSidecar.siblingOf(".hidden")

        // Здесь последняя точка — в начале имени: KOReader видит расширение `hidden`.
        assertThat(sibling).isEqualTo(".sdr/metadata.hidden.lua")
    }
}
