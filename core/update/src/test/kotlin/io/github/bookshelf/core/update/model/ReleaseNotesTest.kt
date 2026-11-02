package io.github.bookshelf.core.update.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Текст «что нового» для диалога (ADR-018).
 *
 * Тело релиза — markdown со скриншотами и таблицами установки. В диалоге нужен короткий
 * человеческий текст: картинки и служебная разметка на e-ink бесполезны, а длинный текст
 * растягивал диалог на весь экран (найдено на устройстве 02.11.2026).
 */
class ReleaseNotesTest {

    @Test
    fun drops_markdown_images_and_headers() {
        val raw = """
            ## BookShelf 2.0.2

            Исправлен возврат скрытых приложений.

            ![Скриншот](https://example.org/a.png)
        """.trimIndent()

        val preview = ReleaseNotes.preview(raw)

        assertThat(preview).contains("BookShelf 2.0.2")
        assertThat(preview).contains("Исправлен возврат скрытых приложений.")
        assertThat(preview).doesNotContain("![")
        assertThat(preview).doesNotContain("##")
    }

    @Test
    fun keeps_bullets_and_collapses_blank_lines() {
        val raw = "Что нового\n\n\n* первое\n* второе"

        val preview = ReleaseNotes.preview(raw)

        assertThat(preview).isEqualTo("Что нового\n* первое\n* второе")
    }

    @Test
    fun truncates_long_text_with_ellipsis() {
        val preview = ReleaseNotes.preview("а".repeat(200), maxChars = 50)

        assertThat(preview.length).isAtMost(51)
        assertThat(preview).endsWith("…")
    }

    @Test
    fun strips_bold_and_code_markers() {
        val preview = ReleaseNotes.preview("* **важно** и `код`")

        assertThat(preview).isEqualTo("* важно и код")
    }

    @Test
    fun empty_body_gives_empty_string() {
        assertThat(ReleaseNotes.preview("")).isEmpty()
        assertThat(ReleaseNotes.preview("   \n\n")).isEmpty()
    }
}
