package io.github.bookshelf.core.readerlink.koreader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Разбор sidecar-файла KOReader (`KoReaderMetadata`).
 *
 * Файл — это Lua-таблица (`return { ... }`), но **выполнять** его нельзя: sidecar лежит на карте и
 * доступен для записи любому приложению с доступом к памяти, поэтому `dofile` на нём означал бы
 * выполнение чужого кода внутри нашего процесса. Разбор идёт по числам и строкам, а всё, что не
 * является числовым или строковым литералом, игнорируется.
 *
 * Фикстура — настоящий файл с устройства (Bigme HiBreak, 05.10.2026): он создан KOReader после
 * перелистывания книги, и проверяются в нём именно те поля, по которым считается прогресс.
 */
class KoReaderMetadataTest {

    @Test
    fun `a real sidecar gives the share already read`() {
        val parsed = checkNotNull(KoReaderMetadata.parse(fixture()))

        // 0.021479713603819 из файла: KOReader хранит долю, а не проценты.
        assertThat(parsed.percentFinished).isWithin(0.000001f).of(0.021479714f)
        assertThat(parsed.pages).isEqualTo(419)
        assertThat(parsed.locator).isEqualTo("/body/DocFragment[5]/body/span/p[24]/text().0")
        assertThat(parsed.documentPath)
            .isEqualTo("/sdcard/Books/Zlotnikov_Vselennaya-neudachnikov_1_Vselennaya-neudachnikov.563756.fb2.epub")
    }

    @Test
    fun `a file without the share gives nothing`() {
        // Нет `percent_finished` — прогресса нет: вернуть 0 % значило бы показать «не начата».
        assertThat(KoReaderMetadata.parse("""return { ["doc_pages"] = 10 }""")).isNull()
    }

    @Test
    fun `a non-numeric share is ignored`() {
        assertThat(KoReaderMetadata.parse("""return { ["percent_finished"] = "половина" }""")).isNull()
    }

    @Test
    fun `a value above one is capped at the whole book`() {
        val parsed = checkNotNull(KoReaderMetadata.parse("""return { ["percent_finished"] = 1.5 }"""))

        assertThat(parsed.percentFinished).isEqualTo(1f)
    }

    @Test
    fun `a negative value is capped at the start`() {
        val parsed = checkNotNull(KoReaderMetadata.parse("""return { ["percent_finished"] = -0.2 }"""))

        assertThat(parsed.percentFinished).isEqualTo(0f)
    }

    @Test
    fun `an expression instead of a value gives nothing`() {
        // Так выглядела бы попытка выполнить код: значение — вызов, а не литерал.
        val text = """return { ["percent_finished"] = os.time(), ["doc_pages"] = 10 }"""

        assertThat(KoReaderMetadata.parse(text)).isNull()
    }

    @Test
    fun `pages and position are optional`() {
        val parsed = checkNotNull(KoReaderMetadata.parse("""return { ["percent_finished"] = 0.5 }"""))

        assertThat(parsed.percentFinished).isEqualTo(0.5f)
        assertThat(parsed.pages).isNull()
        assertThat(parsed.locator).isNull()
    }

    @Test
    fun `an empty file gives nothing`() {
        assertThat(KoReaderMetadata.parse("")).isNull()
    }

    /** Настоящий sidecar с устройства: он же используется адаптером в тестах источника. */
    private fun fixture(): String = checkNotNull(
        javaClass.getResourceAsStream("/koreader/metadata.epub.lua"),
    ) { "нет фикстуры sidecar" }.bufferedReader().use { it.readText() }
}
