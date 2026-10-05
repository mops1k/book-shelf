package io.github.bookshelf.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Адрес копии книги, который получает читалка.
 *
 * ДЕФЕКТ (устройство, 05.10.2026): в `AppModule` authority провайдера стоял литералом
 * (`"${'$'}{context.packageName}.fileprovider"`), подстановки не было, и любая попытка отдать
 * читалке копию роняла приложение: `IllegalArgumentException: Couldn't find meta-data for provider
 * with authority ${'$'}{context.packageName}.fileprovider`. Так запасной путь запуска читалки
 * (копия книги) пропадал целиком — при том что основной путь у FBReader на `fb2.zip` не работает.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ExportedUriFactoryTest {

    @Test
    fun `the copy is addressed with the authority of this app`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val copy = File(context.cacheDir, "export/книга.epub").apply {
            parentFile?.mkdirs()
            writeText("книга")
        }

        val uri = AppModule.provideExportedUriFactory(context).uriFor(copy)

        assertThat(uri.authority).isEqualTo("${context.packageName}.fileprovider")
        assertThat(uri.lastPathSegment).isEqualTo("книга.epub")
    }
}
