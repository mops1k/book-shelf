package io.github.bookshelf.core.storage.files

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Test

/**
 * Чтение диапазона байт из потока (`InputStream.readRange`).
 *
 * Появилось из дефекта, который нашёл lint: воркер разбора метаданных вызывал `readNBytes`, а этот
 * метод есть только с API 33 — при `minSdk 24` на телефоне с Android 7–12 вызов упал бы
 * `NoSuchMethodError`. На нашем единственном устройстве (Android 14) дефект не проявлялся, поэтому
 * проверять его надо тестом, а не рукой.
 *
 * Тест сторожит четыре случая: точное попадание, конец файла раньше запрошенной длины, чтение за
 * концом и короткие «чтения» источника, из-за которых одну длину нельзя брать одним вызовом.
 */
class StreamRangesTest {

    @Test
    fun `reads the requested range`() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6))

        val actual = stream.readRange(offset = 2, length = 3)

        assertThat(actual).isEqualTo(byteArrayOf(3, 4, 5))
    }

    @Test
    fun `requested length beyond the end returns what is there`() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3))

        val actual = stream.readRange(offset = 1, length = 10)

        assertThat(actual).isEqualTo(byteArrayOf(2, 3))
    }

    @Test
    fun `offset beyond the end returns nothing`() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3))

        val actual = stream.readRange(offset = 10, length = 4)

        assertThat(actual).isEmpty()
    }

    @Test
    fun `short reads from the source are collected into one range`() {
        // Источник отдаёт по одному байту: одиночное чтение вернуло бы один байт вместо трёх.
        val stream = DripInputStream(byteArrayOf(10, 20, 30, 40))

        val actual = stream.readRange(offset = 1, length = 3)

        assertThat(actual).isEqualTo(byteArrayOf(20, 30, 40))
    }

    /** Поток, который отдаёт не больше одного байта за вызов — так ведут себя сетевые источники. */
    private class DripInputStream(private val bytes: ByteArray) : InputStream() {

        private var position = 0

        override fun read(): Int =
            if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            if (length <= 0) return 0
            buffer[offset] = bytes[position++]
            return 1
        }

        override fun skip(count: Long): Long {
            val step = minOf(count, (bytes.size - position).toLong()).toInt()
            position += step
            return step.toLong()
        }
    }
}
