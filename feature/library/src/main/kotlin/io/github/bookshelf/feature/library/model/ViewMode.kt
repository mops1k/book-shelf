package io.github.bookshelf.feature.library.model

/**
 * Режим отображения полки (docs/eink-ux.md §6.2).
 *
 * Это UI-состояние, а не домен: в `core:data` режима отображения нет и не будет,
 * поэтому enum живёт в feature:library.
 */
enum class ViewMode {
    /** Сетка обложек — режим по умолчанию. */
    GRID,

    /** Список: обложка + две строки текста. */
    LIST,

    /** Компактный список без обложек — для быстрых устройств и больших экранов. */
    COMPACT,
}
