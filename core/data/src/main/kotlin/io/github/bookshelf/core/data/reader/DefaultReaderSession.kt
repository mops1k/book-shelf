package io.github.bookshelf.core.data.reader

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.common.time.AppClock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сессия чтения поверх `SharedPreferences`.
 *
 * Хранилище, а не память процесса: на слабом устройстве система вправе убить наше приложение, пока
 * человек читает. После перезапуска отметка должна остаться — иначе фоновая работа пойдёт в полную
 * силу прямо во время чтения, и зависания вернутся.
 *
 * Отметка устаревает ([SESSION_TTL_MILLIS]): если читалку закрыли, а в приложение не вернулись
 * (например, устройство выключили), работа не должна оставаться запрещённой навсегда.
 */
@Singleton
class DefaultReaderSession @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: AppClock,
) : ReaderSession {

    /**
     * Своё хранилище, а не настройки: это состояние сеанса, а не выбор человека, и в файле настроек
     * ему делать нечего (иначе оно попало бы в экспорт настроек и в сброс к умолчаниям).
     */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Наше приложение на экране.
     *
     * В памяти, а не в настройках: это состояние процесса, и после перезапуска приложение по
     * определению стартует на экране — при первом же `onAppForeground` флаг встанет сам.
     */
    private val appInForeground = AtomicBoolean(false)

    override fun markOpened(bookId: String) {
        prefs.edit()
            .putString(KEY_BOOK_ID, bookId)
            .putLong(KEY_OPENED_AT, clock.nowMillis())
            .commit()
    }

    override fun isActive(): Boolean {
        // Пока наше приложение на экране, чтения «поверх нас» нет: работа никому не мешает.
        if (appInForeground.get()) return false
        val openedAt = prefs.getLong(KEY_OPENED_AT, 0L)
        if (openedAt == 0L) return false
        if (clock.nowMillis() - openedAt > SESSION_TTL_MILLIS) {
            clear()
            return false
        }
        return true
    }

    override fun clear() {
        prefs.edit().remove(KEY_BOOK_ID).remove(KEY_OPENED_AT).commit()
    }

    /**
     * Возврат в приложение.
     *
     * Отметку снимаем, только если она не «только что поставлена»: мгновенный возврат сразу после
     * запуска читалки (системный выбор приложения, отказ читалки) не должен оставлять сессию на
     * [SESSION_TTL_MILLIS]. Если же человек действительно читал, отметка старше [GRACE_MILLIS] и
     * снимается — работа продолжится.
     */
    override fun onAppForeground() {
        appInForeground.set(true)
        val openedAt = prefs.getLong(KEY_OPENED_AT, 0L)
        if (openedAt != 0L && clock.nowMillis() - openedAt > GRACE_MILLIS) {
            prefs.edit().remove(KEY_BOOK_ID).remove(KEY_OPENED_AT).apply()
        }
    }

    override fun onAppBackground() {
        appInForeground.set(false)
    }

    internal companion object {
        const val PREFS_NAME = "bookshelf_reader_session"
        const val KEY_BOOK_ID = "reader_session_book_id"
        const val KEY_OPENED_AT = "reader_session_opened_at"

        /** Двенадцать часов: дольше одной сессии чтения не бывает, а зависшая отметка вредна. */
        const val SESSION_TTL_MILLIS = 12L * 60L * 60L * 1000L

        /** Пять секунд: мгновенный возврат после запуска — это не чтение. */
        const val GRACE_MILLIS = 5_000L
    }
}
