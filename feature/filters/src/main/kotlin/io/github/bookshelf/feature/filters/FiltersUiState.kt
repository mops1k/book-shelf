package io.github.bookshelf.feature.filters

import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.ReadingStatus

import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet

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
)

/** Какой фасет показывает секция — по этому признаку экран понимает, выбран ли чип. */
enum class FacetKind {
    AUTHORS,
    SERIES,
    TAGS,
    FORMATS,
    STATUSES,
}

/** Один чип фасета: подпись и число книг, которые останутся после выбора. */
data class FacetChipUi(
    /** Идентификатор значения фасета (`authors.id`, `seriesName`, `READING`, …). */
    val id: String,
    /** Подпись для пользователя. */
    val label: String,
    /** Сколько книг у этого значения с учётом остальных активных фильтров. */
    val count: Int,
)

/** Секция фасета: заголовок и чипы. Пустые секции не показываются. */
data class FacetSectionUi(
    /** К какому фасету относится секция. */
    val kind: FacetKind,
    /** Заголовок: «Авторы», «Серии», «Жанры и теги», «Форматы», «Статус чтения». */
    val title: String,
    /** Чипы секции с числами книг. */
    val items: List<FacetChipUi>,
)

/**
 * Маппинг доменного [FacetSet] в секции UI.
 *
 * Единственное место, где экран касается формы доменной модели: дальше он работает
 * с [FacetSectionUi] и не зависит от изменений в `core:data`.
 */
fun FacetSet.toSectionsUi(): List<FacetSectionUi> = listOf(
    FacetSectionUi(kind = FacetKind.AUTHORS, title = "Авторы", items = authors.toChipsUi()),
    FacetSectionUi(kind = FacetKind.SERIES, title = "Серии", items = series.toChipsUi()),
    FacetSectionUi(kind = FacetKind.TAGS, title = "Жанры и теги", items = tags.toChipsUi()),
    FacetSectionUi(kind = FacetKind.FORMATS, title = "Форматы", items = formats.toChipsUi()),
    FacetSectionUi(kind = FacetKind.STATUSES, title = "Статус чтения", items = statuses.toChipsUi()),
).filter { section -> section.items.isNotEmpty() }

private fun List<FacetItem>.toChipsUi(): List<FacetChipUi> = map { item ->
    FacetChipUi(id = item.id, label = item.label, count = item.count)
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
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)

/** Восстановление выбора из применённого запроса: экран открывается с тем, что видит полка. */
internal fun filterSelectionFromQuery(query: BookQuery): FilterSelectionUi =
    FilterSelectionUi(
        authors = query.authorNames,
        series = query.series,
        formats = query.formats,
        statuses = query.statuses.map { it.name }.toSet(),
        tags = query.tags,
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
)

/** Неизвестное имя статуса из старой версии не должно ронять экран. */
private fun String.toReadingStatus(): ReadingStatus? =
    ReadingStatus.entries.firstOrNull { status -> status.name.equals(this, ignoreCase = true) }

/** Пуст ли запрос: у пустого набора фильтров нет отдельного предиката в `:core:data`. */
internal fun BookQuery.isEmptyQuery(): Boolean =
    authorNames.isEmpty() && series.isEmpty() && formats.isEmpty() &&
        statuses.isEmpty() && tags.isEmpty() && !favoritesOnly && searchTerm == null
