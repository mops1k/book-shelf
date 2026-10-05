package io.github.bookshelf.core.data.repository.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.RecordingAppLogger
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Каким адресом книга уходит читалке.
 *
 * ДЕФЕКТ (Александр, 05.10.2026): FBReader открывал «Вселенную неудачников» вместо выбранной книги.
 * Причина: читалка принимает `VIEW` с любым MIME и по нашему `content://` книгу не открывает, а
 * остаётся на прежней. Повторить запуск нельзя (система блокирует старт из фона), поэтому адрес
 * выбирается заранее: FBReader — копия книги, остальные — наша ссылка.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class LaunchAddressChooserTest {

    @Test
    fun `fbReader gets a copy of the book`() {
        // Копию читалка берёт через `FileProvider`: имя с расширением, грант на чтение. По нашему
        // `content://` она книгу не открывает — остаётся на прежней.
        val address = chooser().addressFor(
            profile = profile(FbReaderPreset.PACKAGE_NAME),
            book = book(),
        )

        assertThat(address).isNull()
    }

    @Test
    fun `other readers keep our link`() {
        val address = chooser().addressFor(
            profile = profile("com.xrz.xreaderV3"),
            book = book(),
        )

        assertThat(address?.toString()).isEqualTo(DOCUMENT_URI)
    }

    @Test
    fun `a reader that does not take links gets a copy`() {
        val address = chooser().addressFor(
            profile = profile("com.example.reader", supportsContentUri = false),
            book = book(),
        )

        assertThat(address).isNull()
    }

    private fun chooser() = LaunchAddressChooser(logger = RecordingAppLogger())

    private fun profile(packageName: String, supportsContentUri: Boolean = true) = ReaderProfile(
        id = packageName,
        name = packageName,
        packageName = packageName,
        activityName = null,
        mimeTypes = emptyList(),
        supportsContentUri = supportsContentUri,
        extrasJson = null,
        isBuiltIn = false,
    )

    private fun book(documentUri: String = DOCUMENT_URI) = BookEntity(
        id = "book:one",
        sourceId = "source:one",
        documentUri = documentUri,
        relativePath = "Лем/книга.epub",
        fileName = "книга.epub",
        extension = "epub",
        mimeType = "application/epub+zip",
        sizeBytes = 1_000L,
        lastModified = 1_000L,
        title = "Книга",
        sortTitle = "Книга",
        authorsText = "",
        addedAt = 1_000L,
        updatedAt = 1_000L,
        lastSeenAt = 1_000L,
    )

    private companion object {
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC%2F%D0%BA%D0%BD%D0%B8%D0%B3%D0%B0.epub"
    }
}
