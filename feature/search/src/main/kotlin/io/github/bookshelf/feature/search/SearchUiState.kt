package io.github.bookshelf.feature.search

/**
 * Строка результата поиска (UI-модель).
 *
 * Подсветка совпадения делается рамкой, а не цветом: цвет не может быть единственным
 * носителем смысла на grayscale-панели (docs/eink-ux.md §1).
 */
data class SearchResultUi(
    /** `books.id` — для перехода в карточку. */
    val id: String,
    /** Название книги. */
    val title: String,
    /** Авторы одной строкой. */
    val authors: String,
    /** Фрагмент текста с совпадением, если FTS его отдаёт. */
    val snippet: String? = null,
    /** Где найдено совпадение: «название», «автор», «аннотация». */
    val matchedField: String? = null,
)

/**
 * Состояние экрана поиска.
 *
 * На этапе каркаса заполнено значением по умолчанию: запросов к FTS нет. После Sprint 4
 * [suggestions] приходят из префиксного запроса (`term*`), [results] — из `books_fts`,
 * [recentQueries] — история последних десяти запросов (docs/architecture.md §3.2).
 */
data class SearchUiState(
    /** Введённый запрос. */
    val query: String = "",
    /** Подсказки автодополнения по текущему префиксу. */
    val suggestions: List<String> = emptyList(),
    /** Найденные книги. */
    val results: List<SearchResultUi> = emptyList(),
    /** Идёт поиск — показывается текстовая строка, без анимации. */
    val isSearching: Boolean = false,
    /** История последних запросов (до 10). */
    val recentQueries: List<String> = emptyList(),
    /** Всего найдено — для подписи «Стр. N/M · K результатов». */
    val total: Int = 0,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)
