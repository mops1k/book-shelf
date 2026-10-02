package io.github.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.readerlink.launch.DefaultReaderLauncher
import io.github.bookshelf.core.readerlink.launch.LaunchRequest
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.debug.DebugReaderActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Запуск книги в читалке на живом устройстве.
 *
 * Проверяется то, что юнит-тесты проверить не могут: настоящее разрешение `PackageManager`
 * и настоящая доставка намерения чужому приложению. Заглушка-читалка (`DebugReaderActivity`,
 * только debug) записывает полученное намерение — по нему видно URI, тип и пакет.
 *
 * Здесь же ловится класс ошибок «книга ушла не туда»: приложение обязано открывать книгу
 * в ВЫБРАННОЙ читалке, а не в первом попавшемся приложении, которое заявило книжный MIME.
 */
@RunWith(AndroidJUnit4::class)
class ReaderLaunchInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        DebugReaderActivity.reset()
    }

    @Test
    fun the_book_reaches_the_chosen_reader_with_its_uri_and_type() {
        val launcher = DefaultReaderLauncher(context)
        val bookUri = DocumentsContract.buildDocumentUri(authority(), BOOK_ID)

        val result = runBlocking {
            launcher.launch(
                LaunchRequest(
                    profile = profile(),
                    documentUri = bookUri,
                    mimeType = EPUB_MIME,
                    extension = "epub",
                ),
            )
        }

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val received = DebugReaderActivity.awaitIntent(INTENT_WAIT_MS)
        assertThat(received?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(received?.data).isEqualTo(bookUri)
        assertThat(received?.type).isEqualTo(EPUB_MIME)
        // Читалка чужого пакета получает доступ к книге только выданным правом на чтение.
        assertThat(received?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION)).isNotEqualTo(0)
    }

    /** Профиль указывает на нашу же заглушку: другого «читателя» в тестовой сборке нет. */
    private fun profile(): ReaderProfile = ReaderProfile(
        id = "debug-reader",
        name = "Debug reader",
        packageName = context.packageName,
        activityName = DebugReaderActivity.NAME,
        mimeTypes = listOf(EPUB_MIME),
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = false,
    )

    private fun authority(): String = "${context.packageName}.debug.saf"

    private companion object {
        const val BOOK_ID = "book2"
        const val EPUB_MIME = "application/epub+zip"
        const val INTENT_WAIT_MS = 3_000L
    }
}
