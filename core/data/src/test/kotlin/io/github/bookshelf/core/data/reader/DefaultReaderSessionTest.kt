package io.github.bookshelf.core.data.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.reader.ActiveReading
import io.github.bookshelf.core.common.testing.FakeAppClock
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Сессия чтения: пока открыта читалка, тяжёлые работы обязаны уступать.
 *
 * Проверяется то, из-за чего фикс может не сработать: сессия не должна быть активной, когда наше
 * приложение само на экране (иначе работа встанет навсегда), не должна пережить свой срок (иначе
 * работа встанет навсегда после закрытия читалки) и не должна сниматься мгновенным возвратом сразу
 * после запуска читалки (системный выбор приложения, отказ читалки).
 */
@RunWith(RobolectricTestRunner::class)
class DefaultReaderSessionTest {

    private val clock = FakeAppClock()
    private lateinit var session: DefaultReaderSession

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(DefaultReaderSession.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        session = DefaultReaderSession(context, clock)
    }

    @Test
    fun `an opened book makes the session active while our app is in the background`() = runTest {
        session.onAppBackground()

        session.markOpened(BOOK_ID)

        assertThat(session.isActive()).isTrue()
    }

    @Test
    fun `the session is not active while our app is on screen`() = runTest {
        session.onAppBackground()
        session.markOpened(BOOK_ID)

        session.onAppForeground()

        // Иначе работа встала бы навсегда: человек в нашем приложении, а отметка осталась.
        assertThat(session.isActive()).isFalse()
    }

    @Test
    fun `a session older than its term is not active and is dropped`() = runTest {
        session.onAppBackground()
        session.markOpened(BOOK_ID)
        clock.advanceByMillis(DefaultReaderSession.SESSION_TTL_MILLIS + 1)

        assertThat(session.isActive()).isFalse()
        // Отметка снята, а не просто проигнорирована: иначе она всплыла бы при следующей проверке.
        session.onAppForeground()
        session.onAppBackground()
        assertThat(session.isActive()).isFalse()
    }

    @Test
    fun `returning to the app after reading clears the session`() = runTest {
        session.onAppBackground()
        session.markOpened(BOOK_ID)
        clock.advanceByMillis(DefaultReaderSession.GRACE_MILLIS + 1)

        session.onAppForeground()
        session.onAppBackground()

        assertThat(session.isActive()).isFalse()
    }

    @Test
    fun `an instant bounce right after the launch does not clear the session`() = runTest {
        session.onAppBackground()
        session.markOpened(BOOK_ID)

        // Системный выбор приложения или отказ читалки: вернулись мгновенно.
        session.onAppForeground()
        session.onAppBackground()

        // Читалка при этом может быть открыта, поэтому отметка обязана выжить.
        assertThat(session.isActive()).isTrue()
    }

    @Test
    fun `explicit clear stops the session`() = runTest {
        session.onAppBackground()
        session.markOpened(BOOK_ID)

        session.clear()

        assertThat(session.isActive()).isFalse()
    }

    @Test
    fun `the reader that opened the book is remembered`() = runTest {
        session.onAppBackground()

        session.markOpened(BOOK_ID, readerProfileId = PROFILE_ID)

        // Пока идёт чтение, спрашивать разрешено только эту читалку (требование Александра,
        // 05.10.2026), поэтому её профиль обязан переживать и перезапуск приложения.
        assertThat(session.active())
            .isEqualTo(ActiveReading(bookId = BOOK_ID, readerProfileId = PROFILE_ID))
    }

    @Test
    fun `a book opened without a profile still makes the session active`() = runTest {
        session.onAppBackground()

        // Старый путь открытия (без профиля) не должен ломать сессию: профиль — уточнение, а не
        // условие.
        session.markOpened(BOOK_ID)

        assertThat(session.active()?.bookId).isEqualTo(BOOK_ID)
        assertThat(session.active()?.readerProfileId).isNull()
    }

    private companion object {
        const val BOOK_ID = "doc:0123456789abcdef"
        const val PROFILE_ID = "koreader"
    }
}
