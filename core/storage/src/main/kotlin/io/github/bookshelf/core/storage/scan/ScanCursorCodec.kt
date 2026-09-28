package io.github.bookshelf.core.storage.scan

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Кодирование курсора для строки `scan_runs.cursorDocumentId`.
 *
 * Повреждённый JSON намеренно превращается в `null`: безопасный повтор скана с корня лучше,
 * чем падение воркера из-за старого или частично записанного значения.
 */
object ScanCursorCodec {

    private val json = Json

    fun encode(cursor: ScanCursor): String = json.encodeToString(ScanCursor.serializer(), cursor)

    fun decode(raw: String): ScanCursor? = try {
        json.decodeFromString(ScanCursor.serializer(), raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
