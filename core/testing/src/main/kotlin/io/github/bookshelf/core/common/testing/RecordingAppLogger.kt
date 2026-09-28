package io.github.bookshelf.core.common.testing

import io.github.bookshelf.core.common.log.AppLogger

/**
 * Логгер, который складывает записи в список вместо logcat.
 *
 * Нужен, чтобы проверять в тестах не «что-то залогировалось», а конкретные сообщения:
 * например, что парсер битого FB2 не бросил исключение наружу, а записал причину.
 */
class RecordingAppLogger : AppLogger {

    data class Entry(
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    private val mutableEntries = mutableListOf<Entry>()

    val entries: List<Entry> get() = mutableEntries.toList()

    fun clear() = mutableEntries.clear()

    fun containsMessage(fragment: String): Boolean = mutableEntries.any { it.message.contains(fragment) }

    override fun v(tag: String, message: String, throwable: Throwable?) =
        record(Level.VERBOSE, tag, message, throwable)

    override fun d(tag: String, message: String, throwable: Throwable?) =
        record(Level.DEBUG, tag, message, throwable)

    override fun i(tag: String, message: String, throwable: Throwable?) =
        record(Level.INFO, tag, message, throwable)

    override fun w(tag: String, message: String, throwable: Throwable?) =
        record(Level.WARN, tag, message, throwable)

    override fun e(tag: String, message: String, throwable: Throwable?) =
        record(Level.ERROR, tag, message, throwable)

    private fun record(level: Level, tag: String, message: String, throwable: Throwable?) {
        mutableEntries += Entry(level = level, tag = tag, message = message, throwable = throwable)
    }
}
