package io.github.bookshelf.core.metadata.zip

import io.github.bookshelf.core.metadata.encoding.CharsetDetector
import io.github.bookshelf.core.metadata.parser.BookInput
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Архив превысил лимиты распаковки: разбирать дальше нельзя (защита от «бомбы», «Форматы» §9).
 *
 * Отдельный тип, а не общее исключение: парсеры отличают превышение лимита от «файл битый» и
 * отдают разные `ParseFailureKind` (`ARCHIVE_LIMIT` против `CORRUPT_FILE`).
 */
internal class ZipLimitExceeded(message: String) : Exception(message)

/**
 * Файл не является ZIP-архивом.
 *
 * `ZipInputStream` на чужих байтах молча не находит ни одной записи, а не бросает исключение:
 * без этой проверки «не архив» выглядел бы как архив без нужного файла метаданных, и книга
 * получала бы заголовок из имени файла вместо честной причины «файл повреждён».
 */
internal class NotZipArchive : Exception("не похоже на ZIP-архив")

/**
 * Потоковый обход ZIP-архивов книги: CBZ, DOCX, ODT («Форматы» §7).
 *
 * Архив читается потоком и по имени записи. Временная копия не нужна, весь архив в память не
 * поднимается: страницы комиксов и картинки DOCX бывают по несколько мегабайт, а метаданные
 * обычно лежат в одном маленьком XML. Лимиты считаются по заголовкам записей ДО распаковки —
 * подозрительный архив отсекается, не разворачиваясь.
 */
internal class ZipEntries(
    private val input: BookInput,
    private val limits: ZipLimits = ZipLimits.DEFAULT,
) {

    /**
     * Обойти записи по порядку, отдав каждую посетителю.
     *
     * Посетитель читает нужные записи сам; неиспользованные пропускает `ZipInputStream` при
     * переходе к следующей.
     */
    fun forEach(visit: (entry: ZipEntry, stream: ZipInputStream) -> Unit) {
        val counters = EntryCounters()
        input.openStream().use { stream ->
            val zip = ZipInputStream(stream)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    counters.account(entry = entry, limits = limits)
                    visit(entry, zip)
                }
            }
        }
        if (counters.isEmpty()) throw NotZipArchive()
    }

    /**
     * Учёт лимитов по заголовкам записей: подозрительный архив отсекается ДО распаковки.
     *
     * Отдельный класс, а не переменные в `forEach`: три уровня вложенности внутри цикла упирались
     * в порог `NestedBlockDepth`, а правило «сколько уже насчитали» читается отдельно от обхода.
     */
    private class EntryCounters {
        private var entries = 0
        private var uncompressed = 0L
        private var compressed = 0L

        /** Ни одной записи: файл не архив (или пустой архив) — разбирать нечего. */
        fun isEmpty(): Boolean = entries == 0

        fun account(entry: ZipEntry, limits: ZipLimits) {
            entries += 1
            uncompressed += entry.size.coerceAtLeast(0L)
            compressed += entry.compressedSize.coerceAtLeast(0L)
            if (limits.isSuspicious(entries, uncompressed, compressed)) {
                throw ZipLimitExceeded("архив подозрителен по размеру распаковки")
            }
        }
    }

    /** Прочитать запись с именем [name] (регистр и папка значения не имеют), не больше [limit] байт. */
    fun readEntry(name: String, limit: Int): ByteArray? {
        var found: ByteArray? = null
        forEach { entry, stream ->
            if (found == null && matches(entry.name, name)) {
                found = readLimited(stream, limit)
            }
        }
        return found
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1_024

        /** `ComicInfo.xml` может лежать в подпапке, а регистр в чужих архивах встречается разный. */
        fun matches(actual: String, expected: String): Boolean =
            actual.substringAfterLast('/').equals(expected.substringAfterLast('/'), ignoreCase = true)

        fun readLimited(stream: InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (total < limit) {
                val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
            return out.toByteArray()
        }
    }
}

/**
 * Текст XML из байтов архива: кодировка определяется по началу файла, BOM отбрасывается.
 *
 * В чужих архивах встречается windows-1251 (её и берём по умолчанию, как в разборах FB2/EPUB),
 * а определение кодировки отдано общему [CharsetDetector].
 */
internal fun decodeXmlText(
    bytes: ByteArray,
    detector: CharsetDetector,
    prefixSize: Int = CHARSET_PREFIX_SIZE,
): String {
    val detected = detector.detect(bytes.copyOf(minOf(bytes.size, prefixSize)), null)
    val charset = runCatching { Charset.forName(detected) }.getOrDefault(Charset.forName(WINDOWS_1251))
    return String(bytes, charset).removePrefix("\uFEFF")
}

private const val CHARSET_PREFIX_SIZE = 4_096
private const val WINDOWS_1251 = "windows-1251"
