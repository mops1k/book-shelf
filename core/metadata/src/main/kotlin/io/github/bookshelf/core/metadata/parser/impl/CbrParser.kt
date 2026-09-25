package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * CBR — RAR-архив с комиксом (docs/formats.md §7).
 *
 * **Только flavor `full`.** Единственная библиотека — `com.github.junrar:junrar`, она под
 * несвободной UnRar License, которую не принимает F-Droid; сборки `fdroid` и `play`
 * собираются без CBR под build-флагом `ENABLE_CBR` (ADR-007, docs/architecture.md §9).
 *
 * О флейворах парсер узнаёт через [FeatureFlags], а не через `BuildConfig`: так поведение
 * «CBR выключен» проверяется тестом с фейковыми флагами, а бизнес-логика не знает о сборках.
 */
class CbrParser(
    private val featureFlags: FeatureFlags,
) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    /** Формат не предлагается пользователю, если сборка собрана без CBR. */
    override fun supports(extension: String, mimeType: String?): Boolean {
        if (!featureFlags.cbrEnabled) return false
        return extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)
    }

    override suspend fun parse(input: BookInput): ParseResult {
        if (!featureFlags.cbrEnabled) {
            return ParseResult.Unsupported
        }
        // STUB(Sprint 6): junrar под ENABLE_CBR, ComicInfo.xml, первая страница как обложка.
        TODO("STUB(Sprint 6): разобрать CBR через junrar (только flavor full, ENABLE_CBR)")
    }

    private companion object {
        const val ID = "cbr"
        const val SPRINT = 6
        val EXTENSIONS = setOf("cbr")
        val MIME_TYPES = setOf("application/vnd.comicbook-rar", "application/x-cbr")
    }
}
