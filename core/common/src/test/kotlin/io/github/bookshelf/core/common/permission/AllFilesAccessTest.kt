package io.github.bookshelf.core.common.permission

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Доступ ко всем файлам в разных сборках.
 *
 * Во flavor `play` разрешения `MANAGE_EXTERNAL_STORAGE` нет вовсе (`ALLOW_ALL_FILES_ACCESS = false`),
 * и приложение не имеет права считать, что доступ есть: иначе оно заведёт источник съёмного
 * носителя «прямым путём» и будет читать пустоту вместо системного выбора папки.
 */
class AllFilesAccessTest {

    @Test
    fun `a build without the permission never claims it is granted`() {
        val access = allFilesAccessOf(allowedInBuild = false, granted = { true })

        assertThat(access.granted()).isFalse()
    }

    @Test
    fun `a build with the permission follows the system answer`() {
        assertThat(allFilesAccessOf(allowedInBuild = true, granted = { true }).granted()).isTrue()
        assertThat(allFilesAccessOf(allowedInBuild = true, granted = { false }).granted()).isFalse()
    }
}
