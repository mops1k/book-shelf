package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Пересборка PNG без потери пикселей (`PngImageOptimizer`).
 *
 * Проверяется два обещания. Первое — пиксели не меняются: их сверяет декодер JDK, а не тот же код,
 * что снимает фильтры. Второе — перебор фильтров действительно работает: на настоящем снимке экрана
 * вариант «все строки без фильтра» даёт заметно меньше байт, чем прежнее поведение «оставить фильтры
 * как в файле», и тест требует именно этого, а не «не хуже».
 *
 * Фикстура настоящая: `image-real.png` — снимок экрана устройства (1072×1448, RGBA, без
 * чересстрочности), взятый из `.dsh/shots/02-polki.png`.
 */
class PngImageOptimizerTest {

    private val optimizer = PngImageOptimizer()

    @Test
    fun `the picture is repacked without changing the pixels`() {
        val source = fixture("image-real.png")

        val result = optimizer.optimize(source)

        assertThat(result).isNotNull()
        assertThat(PngTestSupport.pixelsDigest(result!!)).isEqualTo(PngTestSupport.pixelsDigest(source))
        assertThat(result.size).isLessThan(source.size)
    }

    @Test
    fun `row filters are chosen, not kept from the file`() {
        val source = fixture("image-real.png")
        // Прежнее поведение: те же строки с теми же фильтрами, но сжатые максимальным уровнем.
        val oldBehaviour = PngTestSupport.deflate(PngTestSupport.idatInflated(source))

        val result = checkNotNull(optimizer.optimize(source))

        // Без перебора фильтров выигрыш был бы ровно таким — значит, перебор работает.
        assertThat(PngTestSupport.idatCompressedLength(result)).isLessThan(oldBehaviour.size)
        assertThat(PngTestSupport.pixelsDigest(result)).isEqualTo(PngTestSupport.pixelsDigest(source))
    }

    @Test
    fun `the rebuilt file keeps the other chunks`() {
        val source = fixture("image-real.png")
        val expected = PngTestSupport.chunks(source)
            .filterNot { (type, _) -> type == "IDAT" }
            .map { (type, _) -> type }

        val result = checkNotNull(optimizer.optimize(source))

        val actual = PngTestSupport.chunks(result)
            .filterNot { (type, _) -> type == "IDAT" }
            .map { (type, _) -> type }
        // Служебные чанки (профиль, гамма) не выбрасываются: их потеря изменила бы то, что видно.
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `not a png is left alone`() {
        assertThat(optimizer.optimize("просто текст".toByteArray())).isNull()
        assertThat(optimizer.optimize(fixture("image-plain.jpg"))).isNull()
    }

    @Test
    fun `a broken png is left alone`() {
        val source = fixture("image-real.png")
        // Портим содержимое IDAT: контрольная сумма сжатого потока не сойдётся.
        val broken = source.copyOf()
        val idatStart = source.size - BROKEN_OFFSET
        broken[idatStart] = (broken[idatStart] + 1).toByte()
        // Обрезанный файл тоже обязан остаться нетронутым.
        val truncated = source.copyOf(source.size / 2)

        assertThat(optimizer.optimize(broken)).isNull()
        assertThat(optimizer.optimize(truncated)).isNull()
    }

    private fun fixture(name: String): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/$name"),
    ) { "нет фикстуры $name" }.use { stream -> stream.readBytes() }

    private companion object {
        /** Смещение внутрь сжатых данных от конца файла: там заведомо лежит `IDAT`, а не заголовок. */
        const val BROKEN_OFFSET = 100
    }
}
