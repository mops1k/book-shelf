package io.github.bookshelf.core.fb2opt

/**
 * Построение таблиц Хаффмана по фактическим данным картинки.
 *
 * В книгах почти всегда стоят «стандартные» таблицы JPEG: они рассчитаны на среднюю фотографию.
 * Если посчитать таблицы по символам **этой** картинки, файл станет меньше, а коэффициенты (то есть
 * пиксели) не изменятся — меняется только способ записи.
 *
 * Алгоритм тот же, что в libjpeg (`jpeg_gen_optimal_table`): символы с редкими значениями получают
 * длинные коды, частые — короткие; длины ограничиваются 16 битами, как требует формат.
 */
internal object HuffmanOptimizer {

    /** Таблица по частотам символов; `null` — считать нечего. */
    fun build(frequencies: IntArray): JpegHuffmanTable? {
        val total = frequencies.sum()
        if (total == 0) return null
        val frequency = IntArray(MAX_SYMBOLS + 1)
        frequencies.copyInto(frequency, endIndex = minOf(frequencies.size, MAX_SYMBOLS))
        // Пустой символ с частотой 1: без него таблица из одного символа выродилась бы в код
        // нулевой длины, а формат такого не допускает.
        frequency[MAX_SYMBOLS] = 1

        val codeSizes = IntArray(MAX_SYMBOLS + 1)
        val chains = IntArray(MAX_SYMBOLS + 1) { NOT_LINKED }
        mergeUntilOneLeft(frequency, codeSizes, chains)

        val bits = IntArray(MAX_CODE_LENGTH_LIMIT + 1)
        for (symbol in 0..MAX_SYMBOLS) {
            val size = codeSizes[symbol]
            if (size > MAX_CODE_LENGTH_LIMIT) return null
            if (size != 0) bits[size]++
        }
        limitToSixteen(bits)
        // Пустой символ в таблицу не попадает: убираем его из самого длинного кода.
        var longest = bits.size - 1
        while (longest > 0 && bits[longest] == 0) longest--
        if (longest == 0) return null
        bits[longest]--
        return assignSymbols(frequencies, bits)
    }

    /**
     * Раздать длины кодов символам: самые частые получают самые короткие.
     *
     * Сортировка по частоте обязательна. После ограничения длин распределение `bits` уже верное,
     * а вот исходные длины символов — нет: у редких символов они могли быть больше 16 бит. Именно
     * поэтому нельзя раздавать длины по прежним значениям (так делает libjpeg и сам признаётся,
     * что это неочевидно): редкий символ остался бы без кода, и таблица получилась бы неполной.
     */
    private fun assignSymbols(frequencies: IntArray, bits: IntArray): JpegHuffmanTable? {
        val ordered = (0 until MAX_SYMBOLS)
            .filter { symbol -> frequencies.getOrElse(symbol) { 0 } > 0 }
            .sortedWith(compareByDescending<Int> { symbol -> frequencies[symbol] }.thenBy { it })
        val values = IntArray(ordered.size)
        var position = 0
        for (length in 1..JpegHuffmanTable.MAX_CODE_LENGTH) {
            repeat(bits[length]) {
                if (position >= ordered.size) return null
                values[position] = ordered[position]
                position++
            }
        }
        if (position != ordered.size) return null
        val tableBits = IntArray(JpegHuffmanTable.MAX_CODE_LENGTH) { bits[it + 1] }
        return JpegHuffmanTable(tableBits, values)
    }

    /**
     * Слить самые редкие символы, пока не останется один: так получаются длины кодов.
     *
     * Слияние двух самых редких узлов и есть построение дерева Хаффмана; длины кодов считаются
     * по цепочкам слитых символов.
     */
    private fun mergeUntilOneLeft(frequency: IntArray, codeSizes: IntArray, chains: IntArray) {
        while (true) {
            val first = smallest(frequency, except = NOT_LINKED)
            val second = smallest(frequency, except = first)
            if (first < 0 || second < 0) return
            frequency[first] += frequency[second]
            frequency[second] = 0
            // Слияние идёт по цепочкам: длина кода растёт у всех символов, которые уже слиты
            // в узел. Ссылку на второй узел ставит КОНЕЦ цепочки первого — иначе дерево
            // получается не тем, и длины кодов уезжают далеко за 16 бит.
            codeSizes[first]++
            var tail = first
            while (chains[tail] >= 0) {
                tail = chains[tail]
                codeSizes[tail]++
            }
            chains[tail] = second
            codeSizes[second]++
            var node = chains[second]
            while (node >= 0) {
                codeSizes[node]++
                node = chains[node]
            }
        }
    }

    /** Индекс символа с наименьшей ненулевой частотой; [except] исключается. */
    private fun smallest(frequency: IntArray, except: Int): Int {
        var best = NOT_LINKED
        for (symbol in frequency.indices) {
            if (symbol == except || frequency[symbol] == 0) continue
            if (best < 0 || frequency[symbol] < frequency[best]) best = symbol
        }
        return best
    }

    /**
     * Уложить длины кодов в 16 бит.
     *
     * Длиннее коды формат не допускает, поэтому редкие символы «раздаются» более коротким: пара
     * длинных кодов заменяется одним коротким и одним удлинённым в другом месте.
     */
    private fun limitToSixteen(bits: IntArray) {
        for (length in bits.size - 1 downTo JpegHuffmanTable.MAX_CODE_LENGTH + 1) {
            while (bits[length] > 0) {
                var shorter = length - 2
                while (shorter > 0 && bits[shorter] == 0) shorter--
                if (shorter == 0) break
                bits[length] -= 2
                bits[length - 1]++
                bits[shorter + 1] += 2
                bits[shorter]--
            }
        }
    }

    private const val MAX_SYMBOLS = JpegHuffmanTable.MAX_SYMBOLS
    private const val NOT_LINKED = -1
    private const val MAX_CODE_LENGTH_LIMIT = 32
}
