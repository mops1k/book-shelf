package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.reader.ActiveReading
import io.github.bookshelf.core.common.reader.ReaderSession

/**
 * Фейк сессии чтения для тестов.
 *
 * По ADR-012 свои интерфейсы в тестах не мокаются: здесь видно и что сессию отметили, и что её
 * сняли, а состояние «приложение на экране» управляется явно — без него не проверить, что работа
 * не встаёт навсегда, пока человек в нашем приложении.
 */
class FakeReaderSession : ReaderSession {

    /** Книги, для которых сессию отметили, в порядке отметок. */
    val opened = mutableListOf<String>()

    /** Сколько раз сессию снимали. */
    var clearCalls: Int = 0
        private set

    /** Ответ на «идёт ли чтение»; тесты выставляют его сами. */
    var active: Boolean = false

    /** Наше приложение на экране. */
    var appForeground: Boolean = false
        private set

    override fun markOpened(bookId: String, readerProfileId: String?) {
        opened += bookId
        openedProfileId = readerProfileId
        active = true
    }

    /** Профиль читалки последней отметки: по нему синхронизация выбирает источник прогресса. */
    var openedProfileId: String? = null

    override fun active(): ActiveReading? = if (isActive()) {
        ActiveReading(bookId = opened.lastOrNull().orEmpty(), readerProfileId = openedProfileId)
    } else {
        null
    }

    override fun isActive(): Boolean = active && !appForeground

    override fun clear() {
        clearCalls += 1
        active = false
    }

    override fun onAppForeground() {
        appForeground = true
    }

    override fun onAppBackground() {
        appForeground = false
    }
}
