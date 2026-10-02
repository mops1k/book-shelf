package io.github.bookshelf.feature.filters

import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.ReadingStatus

import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.designsystem.R as DesignR
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.genreText
import io.github.bookshelf.core.designsystem.text.readingStatusText

/**
 * Текущий выбор пользователя по всем фасетам.
 *
 * Множества строк (идентификаторы фасетов), а не доменные типы: в Sprint 4 модель
 * один в один превращается в `BookQuery` репозитория, а экран не зависит от его формы.
 * Чистые операции над выбором — в `FilterChipState.kt`.
 */
data class FilterSelectionUi(
    /** Выбранные авторы (идентификаторы `authors.id`). */
    val authors: Set<String> = emptySet(),
    /** Выбранные серии. */
    val series: Set<String> = emptySet(),
    /** Выбранные форматы: `FB2`, `EPUB`, … */
    val formats: Set<String> = emptySet(),
    /** Выбранные статусы чтения (идентификаторы статусов). */
    val statuses: Set<String> = emptySet(),
    /** Выбранные жанры и теги. */
    val tags: Set<String> = emptySet(),
    /** Выбранные источники (папки-источники): идентификаторы `sources.id`. */
    val sources: Set<String> = emptySet(),
)

/** Какой фасет показывает секция — по этому признаку экран понимает, выбран ли чип. */
enum class FacetKind {
    AUTHORS,
    SERIES,
    TAGS,
    FORMATS,
    STATUSES,
    SOURCES,
}

/** Один чип фасета: подпись и число книг, которые останутся после выбора. */
data class FacetChipUi(
    /** Идентификатор значения фасета (`authors.id`, `seriesName`, `READING`, …). */
    val id: String,
    /**
     * Значение подписи: имя автора или серии (данные) либо код жанра/статуса.
     *
     * Показывается как есть, когда [labelText] нет: имя автора — это данные, а не текст интерфейса.
     */
    val labelCode: String,
    /**
     * Подпись, выбранная интерфейсом, если значение — код (`genreText`, `readingStatusText`).
     *
     * Живёт в UI-модели, а не в `:core:data`: у слоя данных нет ресурсов, и готовая русская строка
     * оттуда показывалась бы по-русски и в английском интерфейсе (docs/tdd.md §8).
     */
    val labelText: AppText? = null,
    /** Сколько книг у этого значения с учётом остальных активных фильтров. */
    val count: Int,
)

/** Секция фасета: заголовок и чипы. Пустые секции не показываются. */
data class FacetSectionUi(
    /** К какому фасету относится секция. */
    val kind: FacetKind,
    /**
     * Заголовок: «Авторы», «Серии», «Жанры и теги», «Форматы», «Статус чтения».
     *
     * Ресурс, а не текст: слова живут в `res/values` (ru) и `values-en`, а выбор заголовка
     * остаётся чистой функцией маппинга.
     */
    val titleRes: Int,
    /** Чипы секции с числами книг. */
    val items: List<FacetChipUi>,
)

/**
 * Маппинг доменного [FacetSet] в секции UI.
 *
 * Единственное место, где экран касается формы доменной модели: дальше он работает
 * с [FacetSectionUi] и не зависит от изменений в `core:data`.
 *
 * [kinds] ограничивает набор критериев: экран правил умной полки пока не умеет условие
 * «книги из этой папки» (его нет и в `SmartRules`), поэтому папки там не показываются.
 */
fun FacetSet.toSectionsUi(
    kinds: Set<FacetKind> = FacetKind.entries.toSet(),
): List<FacetSectionUi> = listOf(
    FacetSectionUi(
        kind = FacetKind.AUTHORS,
        titleRes = R.string.filters_section_authors,
        items = authors.toChipsUi(FacetKind.AUTHORS),
    ),
    FacetSectionUi(
        kind = FacetKind.SERIES,
        titleRes = R.string.filters_section_series,
        items = series.toChipsUi(FacetKind.SERIES),
    ),
    FacetSectionUi(
        kind = FacetKind.TAGS,
        titleRes = R.string.filters_section_tags,
        items = tags.toChipsUi(FacetKind.TAGS),
    ),
    FacetSectionUi(
        kind = FacetKind.FORMATS,
        titleRes = R.string.filters_section_formats,
        items = formats.toChipsUi(FacetKind.FORMATS),
    ),
    FacetSectionUi(
        kind = FacetKind.STATUSES,
        titleRes = R.string.filters_section_statuses,
        items = statuses.toChipsUi(FacetKind.STATUSES),
    ),
    FacetSectionUi(
        kind = FacetKind.SOURCES,
        titleRes = R.string.filters_section_sources,
        items = sources.toChipsUi(FacetKind.SOURCES),
    ),
).filter { section -> section.kind in kinds && section.items.isNotEmpty() }

/**
 * Чипы одной секции.
 *
 * Подпись выбирается по виду фасета: жанры и статусы — коды, слова для них берутся из ресурсов
 * (`genreText`, `readingStatusText`); авторы, серии и форматы — данные, показываются как есть.
 */
private fun List<FacetItem>.toChipsUi(kind: FacetKind): List<FacetChipUi> = map { item ->
    FacetChipUi(
        id = item.id,
        labelCode = item.labelCode,
        labelText = when (kind) {
            FacetKind.TAGS -> genreText(item.labelCode)
            FacetKind.STATUSES -> readingStatusText(item.labelCode)
            // Имя папки — данные, но у выбранного в новой версии источника имя пустое:
            // тогда слова выбирает приложение, а не файл (docs/tdd.md §8).
            FacetKind.SOURCES -> if (item.labelCode.isBlank()) AppText(DesignR.string.source_default_name) else null
            else -> null
        },
        count = item.count,
    )
}

/**
 * Состояние экрана фасетных фильтров.
 *
 * [facets] равен `null`, пока данные не пришли: это состояние «загрузка», а не пустая
 * библиотека. После Sprint 4 заполняется `FilterRepository.facets(sourceId, selection)`,
 * а [resultCount] — отдельным `COUNT(*)` по текущему выбору.
 */
data class FiltersUiState(
    /** Фасеты с числами книг; `null` — ещё не загружены. */
    val facets: FacetSet? = null,
    /** Текущий выбор пользователя по всем фасетам. */
    val selected: FilterSelectionUi = FilterSelectionUi(),
    /** Сколько книг останется после применения выбранных фильтров. */
    val resultCount: Int = 0,
    /** Идёт загрузка фасетов или пересчёт результата. */
    val isLoading: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`: ресурс с причиной, а не готовая строка (docs/tdd.md §5). */
    val error: AppText? = null,
)

/** Восстановление выбора из применённого запроса: экран открывается с тем, что видит полка. */
internal fun filterSelectionFromQuery(query: BookQuery): FilterSelectionUi =
    FilterSelectionUi(
        authors = query.authorNames,
        series = query.series,
        formats = query.formats,
        statuses = query.statuses.map { it.name }.toSet(),
        tags = query.tags,
        sources = query.sourceIds,
    )

/**
 * Выбор фасетов в запрос.
 *
 * Одна реализация на фильтры библиотеки и на правила умной полки: правила — это тот же набор
 * условий, и второй сборщик запроса разошёлся бы с первым.
 */
internal fun FilterSelectionUi.toBookQuery(): BookQuery = BookQuery(
    authorNames = authors,
    series = series,
    formats = formats,
    statuses = statuses.mapNotNull { value -> value.toReadingStatus() }.toSet(),
    tags = tags,
    sourceIds = sources,
)

/** Неизвестное имя статуса из старой версии не должно ронять экран. */
private fun String.toReadingStatus(): ReadingStatus? =
    ReadingStatus.entries.firstOrNull { status -> status.name.equals(this, ignoreCase = true) }

/** Пуст ли запрос: у пустого набора фильтров нет отдельного предиката в `:core:data`. */
internal fun BookQuery.isEmptyQuery(): Boolean =
    authorNames.isEmpty() && series.isEmpty() && formats.isEmpty() &&
        statuses.isEmpty() && tags.isEmpty() && sourceIds.isEmpty() &&
        !favoritesOnly && searchTerm == null
