package io.github.bookshelf.feature.catalogs

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Просьба «открой этот фид в браузере каталога».
 *
 * Нужна из-за конкретного требования: в карточке книги касание автора должно открывать его книги
 * и серии, а касание серии — книги этой серии. Карточка записи — отдельный экран со своим
 * ViewModel, и дотянуться до браузера каталога ей нечем: браузер остался ниже в стеке навигации.
 *
 * Существует как общее состояние сессии (как `FilterSelection` на полке): карточка кладёт просьбу,
 * браузер каталога её забирает. Альтернатива — перейти на новый экран браузера с аргументом, но
 * тогда теряется стек уровней, и «Назад» из фида автора не вернула бы на прежний фид.
 */
@Singleton
class CatalogFeedRequest @Inject constructor() {

    private val _request = MutableStateFlow<Request?>(null)

    /** Текущая просьба; `null` — открывать нечего. */
    val request: StateFlow<Request?> = _request.asStateFlow()

    /** Попросить браузер открыть фид; [title] — подпись уровня в хлебных крошках. */
    fun open(catalogId: String, feedUrl: String, title: String) {
        _request.value = Request(catalogId = catalogId, feedUrl = feedUrl, title = title)
    }

    /**
     * Забрать просьбу.
     *
     * Снимается сразу после обработки: иначе возврат в браузер каталога снова открывал бы тот же
     * фид и затирал то, что пользователь успел открыть после.
     */
    fun consume() {
        _request.value = null
    }

    /** Что именно открыть: каталог, адрес фида и подпись уровня. */
    data class Request(
        val catalogId: String,
        val feedUrl: String,
        val title: String,
    )
}
