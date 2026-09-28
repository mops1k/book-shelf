package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.data.model.BookQuery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Общий выбор фильтров между полкой и экраном фильтров.
 *
 * Существует из-за конкретной поломки: у каждого экрана было своё состояние выбора, поэтому
 * экран фильтров пересчитывал счётчики у чипов корректно, а полка после «Применить» показывала
 * все книги — команда [BookRepository.observePage] с фильтром до неё не доходила. Репозиторий
 * это не чинит: считывал он правильный запрос, не доходило применение.
 *
 * **Одно состояние на приложение, а не на экран.** Иначе при уходе с экрана фильтров и
 * возвращении чипы сбрасывались бы, и счётчики на них описывали бы не то, что показывает полка.
 *
 * Хранится в памяти, а не в базе: это состояние сессии, и переживать перезапуск ему не нужно —
 * полка и так начнёт с чистой библиотеки.
 */
@Singleton
class FilterSelection @Inject constructor() {

    private val _query = MutableStateFlow(BookQuery())

    /** Текущий выбор. Обе подписки читают одно и то же. */
    val query: StateFlow<BookQuery> = _query.asStateFlow()

    /**
     * Применить выбор.
     *
     * Проверка на равенство — не микрооптимизация: без неё экран фильтров перезапускал бы
     * запрос полки на каждый клик по чипу, который ничего не изменил.
     */
    fun apply(query: BookQuery) {
        if (_query.value != query) _query.value = query
    }

    fun clear() {
        if (_query.value != BookQuery()) _query.value = BookQuery()
    }
}
