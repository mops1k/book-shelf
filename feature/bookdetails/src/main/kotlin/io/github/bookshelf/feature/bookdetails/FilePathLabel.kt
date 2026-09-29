package io.github.bookshelf.feature.bookdetails

/**
 * Подпись пути к файлу книги для карточки.
 *
 * Показывается папка-источник и место внутри неё: «Books / Лем/Солярис.fb2». Одного
 * относительного пути мало — когда папок несколько, по нему не понять, откуда книга; а без
 * относительного пути не видно, в какой подпапке она лежит.
 *
 * Раньше строка «Путь» в карточке была пустой всегда: состояние объявляло поле, а маппинг его
 * не заполнял (найдено 28.09.2026 по вопросу Александра «путь не заполнен»).
 */
fun filePathLabel(sourceName: String?, relativePath: String): String = when {
    relativePath.isBlank() -> sourceName?.takeIf { it.isNotBlank() }.orEmpty()
    sourceName.isNullOrBlank() -> relativePath
    else -> "$sourceName / $relativePath"
}

