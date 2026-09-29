package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.model.ParseState
import org.junit.Test

/**
 * Строка книги после изменения файла.
 *
 * Дефект, ради которого это проверяется: скан обновлял размер и время файла, но не сбрасывал
 * состояние разбора. `MetadataWorker` выходит сразу, если `parseState = OK`, поэтому заменённый
 * файл книги навсегда оставался с метаданными прежнего (проверено чтением кода 30.09.2026).
 */
class BookFileChangeTest {

    @Test
    fun `changed file asks for a new parse`() {
        val parsed = book(parseState = ParseState.OK, parseError = null, coverPath = "covers/old.webp")

        val updated = parsed.afterFileChange(
            documentUri = "content://tree/primary%3ABooks/document/primary%3ABooks%2Fnew.fb2",
            relativePath = "Автор/new.fb2",
            fileName = "new.fb2",
            extension = "fb2",
            mimeType = "application/fb2",
            sizeBytes = 2048L,
            lastModified = 999L,
            now = 1000L,
        )

        assertThat(updated.parseState).isEqualTo(ParseState.PENDING)
        // Причина прежнего отказа больше не относится к этому файлу.
        assertThat(updated.parseError).isNull()
        // Обложка прежнего файла не должна остаться: файл кэша уже удалён.
        assertThat(updated.coverPath).isNull()
    }

    @Test
    fun `changed file updates the facts about the file`() {
        val parsed = book(parseState = ParseState.OK, parseError = null, coverPath = null)

        val updated = parsed.afterFileChange(
            documentUri = "content://tree/primary%3ABooks/document/primary%3ABooks%2Fnew.fb2",
            relativePath = "Автор/new.fb2",
            fileName = "new.fb2",
            extension = "fb2",
            mimeType = null,
            sizeBytes = 2048L,
            lastModified = 999L,
            now = 1000L,
        )

        assertThat(updated.sizeBytes).isEqualTo(2048L)
        assertThat(updated.lastModified).isEqualTo(999L)
        assertThat(updated.relativePath).isEqualTo("Автор/new.fb2")
        assertThat(updated.lastSeenAt).isEqualTo(1000L)
        // Тип не пришёл от провайдера — остаётся прежний, а не пустая строка.
        assertThat(updated.mimeType).isEqualTo("application/fb2")
    }

    @Test
    fun `a failed parse of the previous file is forgotten`() {
        val failed = book(parseState = ParseState.FAILED, parseError = "Формат не поддерживается", coverPath = null)

        val updated = failed.afterFileChange(
            documentUri = "content://tree/primary%3ABooks/document/primary%3ABooks%2Fnew.epub",
            relativePath = "new.epub",
            fileName = "new.epub",
            extension = "epub",
            mimeType = null,
            sizeBytes = 1L,
            lastModified = 1L,
            now = 2L,
        )

        // Новый файл — новый разбор: прежняя ошибка не должна показываться человеку.
        assertThat(updated.parseState).isEqualTo(ParseState.PENDING)
        assertThat(updated.parseError).isNull()
    }

    private fun book(parseState: ParseState, parseError: String?, coverPath: String?) = BookEntity(
        id = "doc:book",
        sourceId = "source-1",
        documentUri = "content://tree/primary%3ABooks/document/primary%3ABooks%2Fold.fb2",
        relativePath = "Автор/old.fb2",
        fileName = "old.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1024L,
        lastModified = 500L,
        title = "Книга",
        sortTitle = "Книга",
        authorsText = "Автор",
        addedAt = 1L,
        updatedAt = 1L,
        lastSeenAt = 1L,
        parseState = parseState,
        parseError = parseError,
        coverPath = coverPath,
    )
}
