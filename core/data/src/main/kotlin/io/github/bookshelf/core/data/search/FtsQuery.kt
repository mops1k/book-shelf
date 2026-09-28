package io.github.bookshelf.core.data.search

/**
 * Пользовательский ввод → запрос для `MATCH`.
 *
 * FTS — не SQL: кавычка, скобка, звёздочка и служебные слова меняют смысл запроса или роняют его
 * с ошибкой. Поэтому строка режется до букв, цифр и пробелов, а слова соединяются **пробелом** —
 * это неявное «и» в FTS, то есть поиск по нескольким словам остаётся «все слова».
 *
 * Явный оператор `AND` здесь писать нельзя. Сборка SQLite в Android собрана без
 * `SQLITE_ENABLE_FTS3_PARENTHESIS`, поэтому действует standard query syntax, где `AND`, `OR` и
 * `NOT` — не операторы, а обычные токены: запрос `steel AND remains` требовал бы наличия слова
 * «and» в самой книге.
 *
 * Проверено на устройстве (Bigme B6, Android 14, 2026-09-28): запрос «steel remains» давал
 * «Найдено: 0» при трёх книгах со словами steel и remains, а «techniques ways» «находил» книгу
 * только потому, что в её названии есть буквальное слово «and» — то есть совпадение было ложным.
 *
 * @return строка для `MATCH` либо `null`, если искать нечего: пустой ввод — это не «вся
 *   библиотека», а пустой результат (`MATCH ''` вернул бы все строки).
 */
internal fun ftsQueryOf(raw: String?): String? {
    val cleaned = raw.orEmpty()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (cleaned.isEmpty()) return null
    return cleaned.split(' ').joinToString(" ")
}
