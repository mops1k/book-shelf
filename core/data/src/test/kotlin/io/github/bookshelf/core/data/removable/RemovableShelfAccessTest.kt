package io.github.bookshelf.core.data.removable

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Доступ к съёмному носителю и связь источника с ним (`RemovableShelf.kt`).
 *
 * Решения здесь определяют поведение, которое человек видит: спросят ли у него разрешение на карту
 * (лишний вопрос — плохо, отсутствие вопроса при отсутствии доступа — полка без книг) и останется ли
 * полка на экране после изъятия носителя (останется, если источник не связать с UUID тома).
 *
 * Проверяются границы: доступ ко всем файлам даёт прямой путь; адрес дерева тома собирается из UUID;
 * источник узнаётся и по адресу SAF-дерева (с экранированным двоеточием и без), и по прямому пути,
 * и регистр UUID не важен; чужой носитель источником не считается.
 */
class RemovableShelfAccessTest {

    @Test
    fun `all files access means the direct path`() {
        assertThat(cardAccessOf(hasAllFilesAccess = true)).isEqualTo(CardAccess.DIRECT_PATH)
    }

    @Test
    fun `without all files access the user is asked for a tree`() {
        assertThat(cardAccessOf(hasAllFilesAccess = false)).isEqualTo(CardAccess.SAF_TREE)
    }

    @Test
    fun `the volume tree uri points at the volume root`() {
        assertThat(volumeTreeUri("1A2B-3C4D"))
            .isEqualTo("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A")
    }

    @Test
    fun `a saf tree source of the volume is recognised`() {
        val escaped = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A"
        val plain = "content://com.android.externalstorage.documents/tree/1A2B-3C4D:"

        assertThat(sourceBelongsToVolume(escaped, "1A2B-3C4D")).isTrue()
        assertThat(sourceBelongsToVolume(plain, "1A2B-3C4D")).isTrue()
    }

    @Test
    fun `a privately mounted volume is recognised by its path`() {
        // Так выглядит том на прошивке без публичной точки /storage/<uuid>: приложение читает его
        // по приватному пути vold (проверено на Bigme B6 Color 02.10.2026).
        assertThat(sourceBelongsToVolume("/mnt/media_rw/1A2B-3C4D", "1A2B-3C4D")).isTrue()
        assertThat(volumeUuidOf("/mnt/media_rw/0436-C297")).isEqualTo("0436-C297")
    }

    @Test
    fun `a source inside the volume is recognised by its path`() {
        assertThat(sourceBelongsToVolume("/storage/1A2B-3C4D/Books", "1A2B-3C4D")).isTrue()
        assertThat(
            sourceBelongsToVolume(
                "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ABooks",
                "1A2B-3C4D",
            ),
        ).isTrue()
    }

    @Test
    fun `the uuid case does not matter`() {
        assertThat(
            sourceBelongsToVolume(
                "content://com.android.externalstorage.documents/tree/1a2b-3c4d%3A",
                "1A2B-3C4D",
            ),
        ).isTrue()
    }

    @Test
    fun `another volume is not this volume`() {
        assertThat(
            sourceBelongsToVolume(
                "content://com.android.externalstorage.documents/tree/5E6F-7A8B%3A",
                "1A2B-3C4D",
            ),
        ).isFalse()
        // Встроенная память носителем не является, даже если карта вставлена.
        assertThat(
            sourceBelongsToVolume(
                "content://com.android.externalstorage.documents/tree/primary%3ABooks",
                "1A2B-3C4D",
            ),
        ).isFalse()
    }
}
