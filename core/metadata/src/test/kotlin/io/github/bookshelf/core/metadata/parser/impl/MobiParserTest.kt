package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * MOBI / AZW3 / PRC — Palm DB → PalmDOC → MOBI → EXTH (docs/formats.md §6).
 *
 * Дефект, ради которого парсер реализован: формат распознавался, но разбора не было — книги
 * MOBI/AZW3 в библиотеке получали заголовок из имени файла и пометку «формат не поддерживается»,
 * хотя заголовок, авторы и обложка лежат в первых килобайтах файла.
 *
 * Фикстура собирается здесь же: настоящие книги Kindle нельзя кладовать в репозиторий (лицензия),
 * а собранный по спецификации файл проверяет разбор честно — его пишет вторая реализация.
 */
class MobiParserTest {

    @Test
    fun `exth records give author, publisher, description and tags`() = runTest {
        val file = mobiFile(
            exth = listOf(
                100 to text("Лев Толстой"),
                100 to text("Софья Толстая"),
                101 to text("Азбука"),
                103 to text("Роман-эпопея"),
                104 to text("978-5-389-06254-2"),
                105 to text("Классика"),
                105 to text("Роман"),
                106 to text("1869-01-01"),
                524 to text("ru"),
            ),
        )

        val result = MobiParser().parse(input(file))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Война и мир")
        assertThat(success.metadata.authors).containsExactly("Лев Толстой", "Софья Толстая").inOrder()
        assertThat(success.metadata.publisher).isEqualTo("Азбука")
        assertThat(success.metadata.description).isEqualTo("Роман-эпопея")
        assertThat(success.metadata.isbn).isEqualTo("978-5-389-06254-2")
        assertThat(success.metadata.tags).containsExactly("Классика", "Роман").inOrder()
        assertThat(success.metadata.publishedYear).isEqualTo(1869)
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
    }

    @Test
    fun `the cover is the record the cover offset points to`() = runTest {
        val file = mobiFile(
            exth = listOf(201 to int32(0)),
            cover = JPEG,
        )

        val result = MobiParser().parse(input(file)) as ParseResult.Success

        // Запись обложки ищется как «первая картинка + coveroffset»: без этого смещения
        // обложкой стал бы первый попавшийся файл, а чаще всего — ничего.
        assertThat(result.cover?.bytes).isEqualTo(JPEG)
        assertThat(result.cover?.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun `an encrypted book still gives its metadata`() = runTest {
        // DRM закрывает текст, но заголовок, EXTH и картинки остаются читаемыми: отказывать
        // в метаданных из-за шифрования значило бы терять их у большинства книг из магазина.
        val file = mobiFile(
            encryption = 2,
            exth = listOf(100 to text("Лев Толстой")),
        )

        val result = MobiParser().parse(input(file)) as ParseResult.Success

        assertThat(result.metadata.authors).containsExactly("Лев Толстой")
    }

    @Test
    fun `a rebuilt title from exth wins over the header name`() = runTest {
        val file = mobiFile(
            title = "Старое имя",
            mobiType = KF8_TYPE,
            exth = listOf(503 to text("Новое имя")),
        )

        val result = MobiParser().parse(input(file)) as ParseResult.Success

        assertThat(result.metadata.title).isEqualTo("Новое имя")
    }

    @Test
    fun `a file without the book marker is reported as corrupt`() = runTest {
        val result = MobiParser().parse(input("это не книга".toByteArray()))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.CORRUPT_FILE)
    }

    @Test
    fun `an empty file is reported as empty`() = runTest {
        val result = MobiParser().parse(input(ByteArray(0)))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.EMPTY_FILE)
    }

    private fun input(bytes: ByteArray) = BookInput(
        fileName = "Война и мир.mobi",
        extension = "mobi",
        mimeType = null,
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            val from = offset.toInt().coerceAtMost(bytes.size)
            val to = (offset + length).toInt().coerceAtMost(bytes.size)
            bytes.copyOfRange(from, to)
        },
    )

    /** Заголовок вида «имя: …\0» — как его пишут в текст книги. */
    private fun text(value: String): ByteArray = (value + "\u0000").toByteArray()

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

    private fun int16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    /**
     * Собрать минимальный, но структурно верный файл: PDB-заголовок, список записей,
     * запись 0 (PalmDOC + MOBI + EXTH + имя), текстовые записи и картинку обложки.
     */
    private fun mobiFile(
        title: String = "Война и мир",
        encryption: Int = 0,
        mobiType: Int = MOBI_TYPE,
        exth: List<Pair<Int, ByteArray>> = emptyList(),
        cover: ByteArray? = null,
    ): ByteArray {
        val exthBytes = exthBlock(exth)
        val titleBytes = title.toByteArray()
        val nameOffset = PALMDOC_HEADER + MOBI_HEADER + exthBytes.size
        val record0 = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 1, 0, 0)) // compression = 1 (без сжатия)
            write(int32(TEXT_LENGTH))
            write(int16(1)) // одна текстовая запись
            write(int16(RECORD_SIZE))
            write(int16(encryption))
            write(int16(0)) // зарезервировано: заголовок PalmDOC ровно 16 байт
            write(mobiHeader(mobiType = mobiType, nameOffset = nameOffset, nameLength = titleBytes.size))
            write(exthBytes)
            write(titleBytes)
        }.toByteArray()

        val records = mutableListOf(record0, byteArrayOf(1, 2, 3))
        if (cover != null) records += cover

        val header = ByteArrayOutputStream().apply {
            write(ByteArray(32)) // имя базы
            write(int16(0)) // атрибуты
            write(int16(0)) // версия
            write(int32(0)) // создание
            write(int32(0)) // изменение
            write(int32(0)) // резервная копия
            write(int32(1)) // номер изменения
            write(int32(0)) // appInfoID
            write(int32(0)) // sortInfoID
            write("BOOK".toByteArray())
            write("MOBI".toByteArray())
            write(int32(1)) // uniqueIdSeed
            write(int32(0)) // nextRecordListID
            write(int16(records.size))
        }

        val dataStart = PDB_HEADER + records.size * RECORD_INFO
        val offsets = mutableListOf<Int>()
        var running = dataStart
        records.forEach { record ->
            offsets += running
            running += record.size
        }
        return ByteArrayOutputStream().apply {
            write(header.toByteArray())
            offsets.forEachIndexed { index, offset ->
                write(int32(offset))
                write(byteArrayOf(0))
                write(byteArrayOf(0, 0, index.toByte())) // уникальный идентификатор записи
            }
            records.forEach { write(it) }
        }.toByteArray()
    }

    private fun mobiHeader(mobiType: Int, nameOffset: Int, nameLength: Int): ByteArray =
        ByteArray(MOBI_HEADER).apply {
            "MOBI".toByteArray().copyInto(this, 0)
            writeInt(4, MOBI_HEADER)
            writeInt(8, mobiType)
            writeInt(12, CODE_PAGE_UTF8)
            writeInt(16, 1)
            writeInt(20, 6)
            writeInt(84, nameOffset)
            writeInt(88, nameLength)
            writeInt(128, EXTH_FLAG)
        }

    private fun exthBlock(records: List<Pair<Int, ByteArray>>): ByteArray {
        val body = ByteArrayOutputStream()
        records.forEach { (type, data) ->
            body.write(int32(type))
            body.write(int32(data.size + 8))
            body.write(data)
        }
        val bodyBytes = body.toByteArray()
        return ByteArrayOutputStream().apply {
            write("EXTH".toByteArray())
            write(int32(bodyBytes.size + 12))
            write(int32(records.size))
            write(bodyBytes)
        }.toByteArray()
    }

    private fun ByteArray.writeInt(at: Int, value: Int) {
        int32(value).copyInto(this, at)
    }

    private companion object {
        const val PDB_HEADER = 78
        const val RECORD_INFO = 8
        const val PALMDOC_HEADER = 16
        const val MOBI_HEADER = 232
        const val MOBI_TYPE = 2
        const val KF8_TYPE = 8
        const val CODE_PAGE_UTF8 = 65_001
        const val TEXT_LENGTH = 3
        const val RECORD_SIZE = 4_096
        const val EXTH_FLAG = 0x40

        /** Минимальная валидная картинка: сигнатура JPEG, дальше содержимое не важно. */
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    }
}
