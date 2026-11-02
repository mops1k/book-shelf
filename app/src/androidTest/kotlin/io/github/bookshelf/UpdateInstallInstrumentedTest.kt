package io.github.bookshelf

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Установка обновления на устройстве (ADR-018, фаза 3).
 *
 * Проверяется то, чего нет на JVM: `FileProvider` реально отдаёт скачанный APK по `content://`
 * (обычный `file://` на Android 7+ запрещён), а `DownloadManager` доступен — на нём держится
 * скачивание. Системный установщик не запускается: он показывает диалог и ждёт человека.
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        "updates",
    )
    private val file = File(dir, "BookShelf-test.apk")

    @After
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun file_provider_serves_downloaded_apk_as_content_uri() {
        dir.mkdirs()
        file.writeBytes(ByteArray(16))

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo(context.packageName + ".fileprovider")
        // Читается через провайдер, а не напрямую с диска — именно это получит установщик.
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
        assertThat(descriptor).isNotNull()
        descriptor?.close()
    }

    @Test
    fun download_manager_is_available_for_update_flow() {
        val manager = context.getSystemService(DownloadManager::class.java)

        assertThat(manager).isNotNull()
    }


}
