package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * TXT — определение кодировки и разбор имени файла (docs/formats.md §7).
 *
 * BOM → XML-декларация (если текст случайно XML) → `juniversalchardet` → эвристика
 * «валидный UTF-8? иначе windows-1251/koi8-r» (см. [io.github.bookshelf.core.metadata.encoding.CharsetDetector]).
 * Метаданные: имя файла (`Автор - Название`, `Фамилия, Имя`, суффиксы `(серия)`, `[серия]`, `кн. N`)
 * и первые ~30 строк с «Автор:»/«Название:». Обложки нет — она генерируется из заголовка.
 */
class TxtParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 3): определить кодировку, разобрать имя файла и первые ~30 строк.
        TODO("STUB(Sprint 3): TXT — кодировка через CharsetDetector, автор/название из имени и первых строк")
    }

    private companion object {
        const val ID = "txt"
        const val SPRINT = 3
        val EXTENSIONS = setOf("txt")
        val MIME_TYPES = setOf("text/plain")
    }
}
