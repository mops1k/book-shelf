package io.github.bookshelf.feature.catalogs

/**
 * Что показывать в теле карточки записи каталога.
 *
 * Дефект, ради которого решение вынесено отдельно (сообщение Александра, 08.10.2026): пока карточка
 * читала фид, экран показывал «Запись не найдена» — пустое название выглядело как отсутствие записи.
 * Признак ровно один: идёт чтение или уже нет.
 */
internal enum class EntryCardPlaceholder {

    /** Запись найдена: метаданные, описание и кнопка «Скачать». */
    CONTENT,

    /** Идёт чтение фида, название ещё не пришло: строка загрузки, а не «не найдена». */
    LOADING,

    /** Чтение закончилось, а записи нет: «Запись не найдена» с объяснением. */
    MISSING,
}

/**
 * Тело карточки: название важнее загрузки, но загрузка важнее пустоты.
 *
 * Проверка «название пусто, значит записи нет» ошибочна именно во время чтения фида — отсюда
 * порядок ветвей.
 */
internal fun entryCardPlaceholder(isLoading: Boolean, hasTitle: Boolean): EntryCardPlaceholder =
    when {
        hasTitle -> EntryCardPlaceholder.CONTENT
        isLoading -> EntryCardPlaceholder.LOADING
        else -> EntryCardPlaceholder.MISSING
    }
