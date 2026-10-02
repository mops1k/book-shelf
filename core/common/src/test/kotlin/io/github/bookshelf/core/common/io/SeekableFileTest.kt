package io.github.bookshelf.core.common.io

import com.google.common.truth.Truth.assertThat
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import org.junit.Test

/**
 * Владение дескриптором канала с seek (находка аудита 28.09.2026).
 *
 * Прежний контракт отдавал вызывающему только канал и терял `ParcelFileDescriptor`: его
 * финализатор закрыл бы файл под живым каналом, когда сборщик мусора соберёт дескриптор. Здесь
 * проверяется, что закрытие одного объекта освобождает и канал, и владельца.
 */
class SeekableFileTest {

    @Test
    fun `closing the handle closes the channel and the owner`() {
        val file: File = File.createTempFile("seekable-", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val channel = FileInputStream(file).channel
        var ownerClosed = false
        val owner = Closeable { ownerClosed = true }
        val handle = SeekableFile(channel, owner)

        val buffer = ByteBuffer.allocate(3)
        handle.channel.read(buffer)
        assertThat(buffer.array()).isEqualTo(byteArrayOf(1, 2, 3))

        handle.close()

        assertThat(channel.isOpen).isFalse()
        assertThat(ownerClosed).isTrue()
        file.delete()
    }

    @Test
    fun `closing twice does not throw`() {
        val file: File = File.createTempFile("seekable-", ".bin")
        val handle = SeekableFile(FileInputStream(file).channel, Closeable { error("второй раз") })

        // Второй вызов безопасен: освобождение ресурса стоит в `finally`, и шум там не нужен.
        handle.close()
        handle.close()
        file.delete()
    }
}
