package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.rar.RarArchive

/**
 * CBR — RAR-архив с комиксом («Форматы» §7).
 *
 * **Только flavor `full`.** Единственная библиотека — `com.github.junrar:junrar`, она под
 * несвободной UnRar License, которую не принимает F-Droid; сборки `fdroid` и `play`
 * собираются без CBR под build-флагом `ENABLE_CBR` (ADR-007, «Архитектура» §9).
 *
 * О флейворах парсер узнаёт через [FeatureFlags], а не через `BuildConfig`: так поведение
 * «CBR выключен» проверяется тестом с фейковыми флагами, а бизнес-логика не знает о сборках.
 * Само чтение RAR приходит портом [RarArchive]: в сборке без библиотеки набор реализаций пуст,
 * и формат честно не предлагается.
 *
 * Правила разбора комикса общие с CBZ и живут в [ComicArchiveParser].
 *
 * @param readers реализации чтения RAR; берётся первая (в сборке она ровно одна).
 */
class CbrParser(
    private val featureFlags: FeatureFlags,
    private val readers: Collection<RarArchive> = emptyList(),
) : BookMetadataParser {

    private val comic = ComicArchiveParser(format = FORMAT)

    override val id: String = ID

    override val sprint: Int = SPRINT

    /** Формат не предлагается, если сборка собрана без CBR или без чтения RAR. */
    override fun supports(extension: String, mimeType: String?): Boolean {
        if (!featureFlags.cbrEnabled || readers.isEmpty()) return false
        return extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)
    }

    override suspend fun parse(input: BookInput): ParseResult {
        if (!featureFlags.cbrEnabled || readers.isEmpty()) {
            return ParseResult.Unsupported
        }
        val reader = readers.first()
        return comic.parse(input.fileName) {
            ComicArchiveEntries { action ->
                // Содержимое записи открывается лениво: на чужих записях разбор распаковку
                // не заказывает (иначе распаковки конкурируют за канал, и запись приходит пустой).
                reader.forEachEntry(input) { entry, open ->
                    action(ComicArchiveEntry(entry.name, entry.sizeBytes, open))
                }
            }
        }
    }

    private companion object {
        const val ID = "cbr"
        const val SPRINT = 6
        const val FORMAT = "CBR"
        val EXTENSIONS = setOf("cbr")
        val MIME_TYPES = setOf("application/vnd.comicbook-rar", "application/x-cbr")
    }
}
