package io.github.bookshelf.core.readerlink.librera

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Разбор файлов Librera.
 *
 * `app-Progress.json` — объект «имя файла книги → запись»: `p` — доля 0..1, `t` — время записи,
 * `pt` — фрагмент текста; остальные поля (`x`, `y`, `z`, `s`) — режим чтения, они нам не нужны.
 * `app-Recent.json` — список недавних книг `{path, time}`, где путь записан либо как
 * `internal-storage:/Books/…`, либо как `/sdcard/Books/…`.
 *
 * Разбор не падает на чужих данных: файл может быть недописан или заменён, и это «данных нет», а не
 * сбой синхронизации. Поэтому снаружи не исключение, а пустой результат.
 */
internal object LibreraJson {

    /** Запись прогресса книги. */
    data class Record(val share: Float, val time: Long, val snippet: String?) {

        /** Доля превращается в проценты и ограничивается книгой: «150 %» на полке нет. */
        val percent: Float get() = (share * PERCENT).coerceIn(0f, PERCENT)
    }

    /** Недавняя книга: путь целиком и время открытия. */
    data class Recent(val path: String, val time: Long) {

        /** Имя файла — единственный ключ, который есть у Librera в прогрессе. */
        val fileName: String get() = path.substringAfterLast('/')
    }

    fun progress(text: String): Map<String, Record> = runCatching {
        Json.parseToJsonElement(text).jsonObject.mapNotNull { (name, element) ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val share = (record["p"] as? JsonPrimitive)?.floatOrNull ?: return@mapNotNull null
            name to Record(
                share = share,
                time = (record["t"] as? JsonPrimitive)?.longOrNull ?: 0L,
                snippet = (record["pt"] as? JsonPrimitive)?.contentOrNull,
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    fun recent(text: String): List<Recent> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val path = (entry["path"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            Recent(path = path, time = (entry["time"] as? JsonPrimitive)?.longOrNull ?: 0L)
        }
    }.getOrDefault(emptyList())

    private const val PERCENT = 100f
}
