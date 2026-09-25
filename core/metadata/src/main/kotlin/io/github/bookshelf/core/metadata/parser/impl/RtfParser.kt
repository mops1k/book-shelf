package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * RTF — низкий приоритет (docs/formats.md §7).
 *
 * Разбор группы `\info`: `\title`, `\author`. `rtfparserkit` не подключаем ради двух полей;
 * допустимо ограничиться именем файла, если разбор окажется дороже пользы.
 */
class RtfParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 6): группа \info (\title, \author); фолбэк — имя файла.
        TODO("STUB(Sprint 6): разобрать RTF \\info (\\title, \\author) или ограничиться именем файла")
    }

    private companion object {
        const val ID = "rtf"
        const val SPRINT = 6
        val EXTENSIONS = setOf("rtf")
        val MIME_TYPES = setOf("application/rtf", "text/rtf")
    }
}
