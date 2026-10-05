package io.github.bookshelf.core.readerlink.xreader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.GZIPInputStream

/**
 * Разбор файла состояния XReader.
 *
 * Файл `/sdcard/XRZBookCache/V3/js<идентификатор>` — это **gzip**, а внутри объект, где нужные данные
 * лежат не полями, а **вложенной строкой JSON** в поле `bookStateInfo`: `bookPath`, `bookName`,
 * `currentPage`, `totalPage`, `bookEndTime`. Проверено на файле устройства 05.10.2026.
 *
 * Разбор не падает на чужих данных: файл может быть недописан, сжат другой версией или заменён — это
 * «данных нет», а не сбой синхронизации. Поэтому наружу `null`, а не исключение.
 */
internal object XReaderJson {

    /** Состояние книги: путь, имя, текущая страница, всего страниц и время окончания чтения. */
    data class State(
        val bookPath: String,
        val bookName: String,
        val currentPage: Int,
        val totalPage: Int,
        val endTime: String?,
    ) {

        /**
         * Доля прочитанного в процентах; `null` — считать нечего.
         *
         * `totalPage = 0` встречается у книги, которую читалка ещё не разобрала: показывать из-за
         * этого 0 % нельзя, на полке это «книга не начата».
         */
        val percent: Float?
            get() = if (totalPage <= 0) {
                null
            } else {
                (currentPage.toFloat() / totalPage * PERCENT).coerceIn(0f, PERCENT)
            }
    }

    fun parse(bytes: ByteArray): State? {
        val text = text(bytes) ?: return null
        return runCatching {
            val outer = Json.parseToJsonElement(text).jsonObject
            // Само состояние — строка с JSON внутри; остальные поля файла (зум, режим, закладки) не нужны.
            val inner = outer["bookStateInfo"]?.jsonPrimitive?.contentOrNull ?: return null
            val state = Json.parseToJsonElement(inner).jsonObject
            state.toState()
        }.getOrNull()
    }

    private fun JsonObject.toState(): State? {
        val path = this["bookPath"]?.jsonPrimitive?.contentOrNull ?: return null
        return State(
            bookPath = path,
            bookName = this["bookName"]?.jsonPrimitive?.contentOrNull ?: path.substringAfterLast('/'),
            currentPage = this["currentPage"]?.jsonPrimitive?.intOrNull ?: return null,
            totalPage = this["totalPage"]?.jsonPrimitive?.intOrNull ?: return null,
            endTime = this["bookEndTime"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Текст файла: сжатый распаковывается, несжатый берётся как есть (версии XReader разные). */
    private fun text(bytes: ByteArray): String? = runCatching {
        if (bytes.isGzip()) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
        } else {
            bytes.decodeToString()
        }
    }.getOrNull()

    private fun ByteArray.isGzip(): Boolean =
        size >= 2 &&
            (this[0].toInt() and BYTE_MASK) == GZIP_FIRST &&
            (this[1].toInt() and BYTE_MASK) == GZIP_SECOND

    private const val PERCENT = 100f

    /** Первые два байта gzip: `1f 8b`. */
    private const val GZIP_FIRST = 0x1F
    private const val GZIP_SECOND = 0x8B
    private const val BYTE_MASK = 0xFF
}
