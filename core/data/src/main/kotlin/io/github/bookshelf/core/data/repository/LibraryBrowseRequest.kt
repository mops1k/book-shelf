package io.github.bookshelf.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Просьба «покажи на полке книги этого автора / этой серии».
 *
 * Зачем: в карточке книги касание автора должно открывать его книги и серии, а касание серии —
 * книги серии. Карточка — отдельный экран со своим ViewModel, и дотянуться до полки ей нечем:
 * полка осталась ниже в стеке навигации.
 *
 * Тот же приём, что у [FilterSelection] на полке и у `CatalogFeedRequest` в каталогах OPDS: карточка
 * кладёт просьбу, полка её забирает. Альтернатива — новый экран полки с аргументами в маршруте, но
 * тогда теряется состояние обзора и «Назад» ведёт не туда, где пользователь был.
 *
 * Хранится в памяти: это состояние сессии, переживать перезапуск ему не нужно.
 */
@Singleton
class LibraryBrowseRequest @Inject constructor() {

    private val _request = MutableStateFlow<Request?>(null)

    /** Текущая просьба; `null` — показывать нечего. */
    val request: StateFlow<Request?> = _request.asStateFlow()

    /** Попросить показать книги автора (в виде «Авторы», с его сериями). */
    fun openAuthor(name: String) {
        if (name.isBlank()) return
        _request.value = Request.Author(name)
    }

    /** Попросить показать книги серии. */
    fun openSeries(name: String) {
        if (name.isBlank()) return
        _request.value = Request.Series(name)
    }

    /**
     * Забрать просьбу.
     *
     * Снимается сразу после обработки: иначе каждое возвращение на полку снова открывало бы того
     * же автора и затирало то, что пользователь выбрал после.
     */
    fun consume() {
        _request.value = null
    }

    /** Что именно показать. */
    sealed interface Request {
        /** Имя автора — оно же ключ фасета (`books.authorsText`). */
        val name: String

        data class Author(override val name: String) : Request

        data class Series(override val name: String) : Request
    }
}
