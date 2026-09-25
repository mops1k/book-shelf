package io.github.bookshelf.core.metadata.zip

/**
 * Лимиты распаковки архива — защита от zip-bomb (docs/formats.md §9).
 *
 * ZIP-контейнеры (EPUB, CBZ, DOCX, ODT, FB2.ZIP) приходят из чужих библиотек, поэтому
 * архив нельзя распаковывать «как есть»: файл на 1 МБ может развернуться в десятки гигабайт.
 *
 * @param maxEntries предел числа записей архива;
 * @param maxUncompressedBytes предел суммарного распакованного объёма;
 * @param maxCompressionRatio предел отношения распакованного объёма к сжатому;
 * @param maxDepth предел вложенности (архив в архиве).
 */
data class ZipLimits(
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    val maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
    val maxCompressionRatio: Double = DEFAULT_MAX_COMPRESSION_RATIO,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {

    /**
     * Подозрителен ли архив по своим метаданным.
     *
     * Функция чистая: парсеры вызывают её до распаковки и при `true` возвращают `Failed`,
     * не читая содержимое.
     *
     * @param entries число записей из центрального каталога;
     * @param uncompressed суммарный распакованный объём по заголовкам;
     * @param compressed суммарный сжатый объём по заголовкам.
     */
    fun isSuspicious(entries: Int, uncompressed: Long, compressed: Long): Boolean {
        if (entries > maxEntries) return true
        if (uncompressed > maxUncompressedBytes) return true
        // Ноль сжатых байт при ненулевом содержимом — вырожденный случай классической бомбы.
        if (compressed <= 0) return uncompressed > 0
        return uncompressed.toDouble() / compressed > maxCompressionRatio
    }

    companion object {

        const val DEFAULT_MAX_ENTRIES = 10_000
        const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 512L * 1_024 * 1_024
        const val DEFAULT_MAX_COMPRESSION_RATIO = 100.0
        const val DEFAULT_MAX_DEPTH = 8

        /** Лимиты по умолчанию; могут быть переопределены настройкой. */
        val DEFAULT = ZipLimits()
    }
}
