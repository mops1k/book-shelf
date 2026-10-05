package io.github.bookshelf.core.readerlink.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Каким intent'ом книга уходит читалке.
 *
 * ДЕФЕКТ (Александр, 05.10.2026: «открываю предыстория книга 1, открывается в фбридере вселенная
 * неудачников»). Причина найдена опытом на устройстве (adb, запущенный FBReader Premium 3.8.31,
 * 06.10.2026):
 * * `ACTION_VIEW` + `file://` + **MIME** + `NEW_TASK|CLEAR_TOP` → книга НЕ открывается;
 * * `ACTION_VIEW` + `file://` + **без MIME** + `NEW_TASK` → книга НЕ открывается;
 * * `ACTION_VIEW` + `file://` + **без MIME** + `NEW_TASK|CLEAR_TOP` → книга открывается.
 *
 * То есть читалке нужен адрес **без типа** и флаг `CLEAR_TOP`: без него уже открытая книга остаётся
 * на экране, и человек видит чужую книгу. Тип (`setDataAndType`) ломает разбор: FBReader объявляет
 * `VIEW` на любой тип, а с явным типом книгу не берёт.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ReaderLauncherIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcher = DefaultReaderLauncher(context)

    @Test
    fun `the book goes without a mime type`() {
        val intent = launcher.viewIntent(BOOK_URI, PACKAGE_NAME, activityName = null)

        // Тип ломает открытие: читалка получает адрес, но книгу не берёт.
        assertThat(intent.type).isNull()
        assertThat(intent.data).isEqualTo(BOOK_URI)
    }

    @Test
    fun `the reader is asked to bring the book to the front`() {
        val intent = launcher.viewIntent(BOOK_URI, PACKAGE_NAME, activityName = null)

        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        // Без CLEAR_TOP уже открытая книга остаётся на экране — это и видел человек.
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotEqualTo(0)
    }

    @Test
    fun `the intent stays inside the chosen reader`() {
        val intent = launcher.viewIntent(BOOK_URI, PACKAGE_NAME, activityName = null)

        assertThat(intent.`package`).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun `an explicit activity of the reader is kept`() {
        val intent = launcher.viewIntent(BOOK_URI, PACKAGE_NAME, activityName = "$PACKAGE_NAME.FBReader")

        assertThat(intent.component?.className).isEqualTo("$PACKAGE_NAME.FBReader")
    }

    private companion object {
        const val PACKAGE_NAME = "com.fbreader"
        val BOOK_URI: Uri = Uri.parse("content://provider/book.fb2.zip")
    }
}
