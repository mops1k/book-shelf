package io.github.bookshelf.core.storage.provider

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Адрес копии книги для внешней читалки.
 *
 * Копия отдаётся через `FileProvider`: читалки, которым нужен «обычный» URI, иначе книгу не видят
 * (ADR-004). Authority обязан совпадать с манифестом (`${applicationId}.fileprovider`) — иначе
 * `FileProvider` роняет приложение, и запасной путь запуска пропадает целиком. Так и случилось
 * (дефект на устройстве 05.10.2026: вместо подстановки пакета в коде стоял литерал
 * `${context.packageName}.fileprovider`, и «Временный экспорт книги» падал).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ExportFileProviderTest {

    @Test
    fun `the authority is the application id with the fileprovider suffix`() {
        assertThat(ExportFileProvider.authority("io.github.bookshelf.debug"))
            .isEqualTo("io.github.bookshelf.debug.fileprovider")
    }

    @Test
    fun `the copy is addressed by its file name`() {
        val uri = ExportFileProvider.uriFor(
            applicationId = "io.github.bookshelf",
            file = File("/data/user/0/io.github.bookshelf/cache/export/книга.epub"),
        )

        // В адрес попадает только имя файла: «имя → каталог» задаёт `file_paths.xml`, и открыт
        // в нём ровно `cacheDir/export`. Кириллица в адресе кодируется процентами — это требование
        // URI, а не потеря имени: `lastPathSegment` возвращает имя как есть.
        assertThat(Uri.parse(uri).lastPathSegment).isEqualTo("книга.epub")
        assertThat(uri).isEqualTo("content://io.github.bookshelf.fileprovider/%D0%BA%D0%BD%D0%B8%D0%B3%D0%B0.epub")
    }
}
