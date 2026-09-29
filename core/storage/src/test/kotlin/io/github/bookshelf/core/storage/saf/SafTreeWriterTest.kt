package io.github.bookshelf.core.storage.saf

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * Удаление файла через SAF (`SafTreeWriter.delete`).
 *
 * Регрессионный тест к дефекту 29.09.2026: удаление шло через `ContentResolver.delete`, а у
 * `DocumentsProvider` базовый `delete()` бросает `UnsupportedOperationException` («Delete not
 * supported») — провайдеры реализуют `deleteDocument`. На устройстве это выглядело так, что
 * «Удалить навсегда» честно сообщало об ошибке, а файл и запись оставались на месте.
 *
 * Поэтому проверяется не текст ошибки, а сам способ вызова: приходит ли провайдеру
 * `METHOD_DELETE_DOCUMENT`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SafTreeWriterTest {

    @Test
    fun `delete calls the provider through the document contract`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = RecordingDocumentsProvider()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        val writer = SafTreeWriterImpl(context)

        val result = writer.delete("content://$AUTHORITY/tree/root/document/doc-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(provider.method).isEqualTo(METHOD_DELETE_DOCUMENT)
        assertThat(provider.deletedDocumentId).isEqualTo("doc-1")
    }

    @Test
    fun `missing document is not an error`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Провайдер ответил «false» — документа нет. Для вызывающего это не ошибка: удалять
        // нечего, и запись книги можно спокойно убирать.
        val provider = RecordingDocumentsProvider(result = false)
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        val writer = SafTreeWriterImpl(context)

        assertThat(writer.delete("content://$AUTHORITY/tree/root/document/doc-1").isSuccess).isTrue()
    }

    /** Провайдер документов, записывающий, каким методом его позвали. */
    private class RecordingDocumentsProvider(
        private val result: Boolean = true,
    ) : ContentProvider() {

        var method: String? = null
            private set

        var deletedDocumentId: String? = null
            private set

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            this.method = method
            // Ключ, под которым провайдер получает адрес документа, — скрытая константа; берём
            // первое значение-`Uri` из набора, чтобы тест не зависел от её написания.
            deletedDocumentId = extras?.keySet()?.firstNotNullOfOrNull { key ->
                @Suppress("DEPRECATION")
                extras.getParcelable<Uri>(key)?.let { uri -> DocumentsContract.getDocumentId(uri) }
            }
            return Bundle().apply { putBoolean(EXTRA_RESULT, result) }
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "com.example.documents"

        /**
         * Значения скрытых констант `DocumentsContract`: в `android.jar` их нет, а провайдеру
         * приходят именно они. Если AOSP их переименует, тест упадёт — и это правильно: удаление
         * файла перестанет работать на устройстве.
         */
        const val METHOD_DELETE_DOCUMENT = "android:deleteDocument"
        const val EXTRA_RESULT = "android:result"
    }
}
