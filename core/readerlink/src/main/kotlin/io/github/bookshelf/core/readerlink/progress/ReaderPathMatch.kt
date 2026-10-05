package io.github.bookshelf.core.readerlink.progress

import java.net.URLDecoder

/**
 * Указывает ли путь, которым читалка называет книгу, на нашу книгу.
 *
 * Читалки с файловым прогрессом (CoolReader, PocketBook, Librera) называют книгу **реальным путём**
 * (`/sdcard/Books/Лем/Книга.fb2.epub`), а у нас адрес документа SAF. Поэтому от пути читалки
 * требуется два признака: он должен кончаться относительным путём книги внутри библиотеки **и**
 * содержать папку самой библиотеки, взятую из адреса документа (`…/tree/primary%3ABooks/…` → `Books`).
 *
 * Одного хвоста мало: у двух книг с одинаковым именем в разных папках он совпадёт, и чужой процент
 * попадёт нашей книге. Когда папку библиотеки определить нельзя (адрес не SAF-дерево — прямой путь
 * или чужой провайдер), остаётся только хвост: это единственный доступный признак, и он лучше, чем
 * полный отказ.
 *
 * Имя файла — не единственный ключ, которым пользуются читалки, но единственный, который у нас есть
 * для всех трёх; при неоднозначности источники лучше не записывают прогресс, чем записывают чужой.
 */
internal fun readerPathMatches(readerPath: String, book: ProgressBookRef): Boolean {
    // Читалка могла запомнить не файл, а адрес, которым мы её открыли: CoolReader сохраняет
    // `/tree/primary:Books/document/primary:Books/Книга.epub` — то же дерево и документ, что в нашем
    // адресе. Это самая точная примета, и проверяем её первой (устройство, 05.10.2026: из-за её
    // отсутствия свежая запись книги проигрывала старой).
    if (book.readerAddress.isNotEmpty() && readerPath.endsWithTailOf(book.readerAddress)) return true
    if (!readerPath.endsWith("/${book.relativePath}")) return false
    // Папка библиотеки известна (адрес SAF-дерева) — требуем и её: одного хвоста мало, иначе
    // «/sdcard/Other/Лем/Книга.fb2.epub» сошёл бы за нашу книгу.
    val libraryFolder = libraryRootFolder(book.documentUri) ?: return true
    return readerPath.contains("/$libraryFolder/")
}

/** Кончается ли путь читалки адресом документа: и раскодированным, и закодированным (`%3A`). */
private fun String.endsWithTailOf(readerAddress: String): Boolean =
    documentTail(readerAddress, decode = true)?.let { endsWith(it) } == true ||
        documentTail(readerAddress, decode = false)?.let { endsWith(it) } == true

/**
 * Часть адреса документа без схемы и провайдера.
 *
 * `content://com.android.externalstorage.documents/tree/primary:Books/document/primary:Books/Книга.epub`
 * → `/tree/primary:Books/document/primary:Books/Книга.epub`: ровно то, что читалка может записать
 * как «путь». Адрес без `//` (прямой путь, чужой провайдер) приметы не даёт — `null`.
 */
private fun documentTail(readerAddress: String, decode: Boolean): String? {
    val address = if (decode) decodeAddress(readerAddress) else readerAddress
    val afterProvider = address.substringAfter("//", missingDelimiterValue = "").substringAfter('/')
    return "/$afterProvider".takeIf { afterProvider.isNotEmpty() }
}

/** Раскодирование `%XX` без Android: адреса SAF кодируют пробелы и двоеточия. */
private fun decodeAddress(address: String): String =
    runCatching { URLDecoder.decode(address, Charsets.UTF_8.name()) }.getOrDefault(address)

/**
 * Имя папки библиотеки из адреса документа.
 *
 * `content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2F…`
 * → `Books`; адрес дерева `…/tree/primary%3ABooks%2F%D0%9B%D0%B5%D0%BC` → `Лем`.
 *
 * `null` означает «адрес не SAF-дерево» (например, прямой путь или чужой провайдер) — тогда сверять
 * папку не с чем, и остаётся только хвост пути.
 *
 * Разбор строковый, без Android: так помощник проверяется обычным тестом, а не Robolectric.
 * `URLDecoder` берётся потому, что адреса SAF кодируют `%XX`; символ `+` в них не встречается и
 * пробелом не станет.
 */
internal fun libraryRootFolder(documentUri: String): String? {
    val tree = documentUri.substringAfter(TREE_MARKER, missingDelimiterValue = "").substringBefore('/')
    if (tree.isEmpty()) return null
    val decoded = runCatching { URLDecoder.decode(tree, Charsets.UTF_8.name()) }.getOrDefault(tree)
    // `primary:Books` → том и путь; имя папки библиотеки — последний элемент пути.
    return decoded.substringAfter(':').substringAfterLast('/').ifEmpty { null }
}

/** Отрезок адреса SAF, после которого идёт идентификатор дерева (`primary:Books`). */
private const val TREE_MARKER = "/tree/"
