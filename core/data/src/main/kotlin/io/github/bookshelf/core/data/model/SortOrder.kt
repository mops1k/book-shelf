package io.github.bookshelf.core.data.model

/**
 * Сортировка полки (docs/eink-ux.md §6.2).
 *
 * Собственный enum `:core:data`, хотя такая же настройка есть в `:core:datastore`
 * (`model.SortOrder`): слой данных не должен зависеть от того, где хранится настройка, а
 * маппинг настройка → запрос делает `:core:data`.
 */
enum class SortOrder {
    TITLE,
    AUTHOR,
    SERIES,
    ADDED_AT,
    LAST_OPENED,
    SIZE,
    PROGRESS,
    YEAR,
}

/**
 * Группировка полки: заголовки-разделители внутри страницы (docs/eink-ux.md §6.2).
 *
 * [NONE] — плоский список; остальные значения задают признак, по которому режется страница.
 */
enum class Grouping {
    NONE,
    SERIES,
    AUTHOR,
    FOLDER,
    FORMAT,
}
