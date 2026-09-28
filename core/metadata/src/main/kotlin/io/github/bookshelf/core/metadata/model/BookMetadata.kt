package io.github.bookshelf.core.metadata.model

/**
 * Метаданные книги, извлечённые парсером или введённые вручную.
 *
 * Набор полей — из docs/formats.md (FB2 `title-info`/`publish-info`, EPUB Dublin Core,
 * MOBI EXTH, CBZ `ComicInfo`, DOCX/ODT core.xml/meta.xml). Репозиторий в `:core:data`
 * раскладывает их по колонкам `books` и денормализует авторов/серию.
 *
 * @param seriesIndex номер в серии; дробный, потому что встречаются «1.5» и «2.5»;
 * @param source происхождение метаданных — приоритет источников задаётся в Sprint 7.
 */
data class BookMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    val tags: List<String> = emptyList(),
    val source: MetadataOrigin = MetadataOrigin.NONE,
) {

    /**
     * Заголовок для сортировки: без ведущих «А/A/An/The», с нормализацией регистра.
     *
     * Нужен отдельным полем `sortTitle` в БД, чтобы «Три мушкетёра» и «The Three Musketeers»
     * не разъезжались по разным концам списка (docs/architecture.md §3.1).
     *
     * Имя группы или персонажа с «The» попадёт под следующую букву (`The Beatles` → `Beatles`):
     * отличить имя собственное от заглавия по тексту нельзя, а предсказуемость поиска важнее.
     */
    fun sortTitleOf(): String {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return trimmedTitle

        val withoutArticle = trimmedTitle.replaceFirst(ARTICLE_PREFIX, "").trim()
        if (withoutArticle.isEmpty()) return title

        val firstCharacter = withoutArticle.first()
        return firstCharacter.uppercaseChar().toString() +
            withoutArticle.drop(1).lowercase()
    }

    private companion object {
        /**
         * Артикли снимаются только вместе с разделителем; граница пробела обязательна,
         * иначе название вроде «Анна Каренина» было бы повреждено.
         */
        val ARTICLE_PREFIX = Regex("(?iu)^(?:А|Аа|An|The)\\s+")
    }
}

/** Источник метаданных — соответствует `metadataSource` в таблице `books`. */
enum class MetadataOrigin {

    /** Из самого файла: FB2 title-info, EPUB DC, MOBI EXTH и т. п. */
    EMBEDDED,

    /** Из имени файла/пути, когда встроенных метаданных нет. */
    FILENAME,

    /** Пользователь отредактировал вручную — имеет высший приоритет. */
    MANUAL,

    /** Метаданные ещё не определены. */
    NONE,
}
