package io.github.bookshelf.feature.search


import io.github.bookshelf.core.designsystem.text.AppText
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
    /** Где найдено совпадение — ресурс подписи («название», «автор», «аннотация») или `null`. */
    val matchedFieldRes: Int? = null,
)

/**
 * Подпись и текст пустого состояния раздела «Результаты».
 *
 * Чистая функция: правило «до поиска не говорим, что совпадений нет» проверяется без Compose.
 */
data class SearchEmptyState(
    val titleRes: Int,
    val messageRes: Int,
    /** Подставляемый аргумент сообщения (искомый термин) или `null`. */
    val messageArg: String? = null,
)

/**
 * Что показать в пустом разделе результатов.
 *
 * @param query введённый запрос;
 * @param searchedTerm термин, по которому поиск уже выполнялся (`null` — не выполнялся).
 */
fun searchEmptyState(query: String, searchedTerm: String?): SearchEmptyState = when {
    query.isBlank() -> SearchEmptyState(
        titleRes = R.string.search_empty_enter_query,
        messageRes = R.string.search_empty_hint,
    )

    searchedTerm == null -> SearchEmptyState(
        titleRes = R.string.search_empty_press_find,
        messageRes = R.string.search_empty_press_find_message,
    )

    else -> SearchEmptyState(
        titleRes = R.string.search_empty_nothing_found,
        messageRes = R.string.search_empty_nothing_found_message,
        messageArg = searchedTerm,
    )
}

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
    /**
     * Термин, по которому поиск **уже** выполнялся; `null` — ещё не искали.
     *
     * Нужен, чтобы экран не врал: пока человек набирает запрос, [results] пуст, и прежний экран
     * сообщал «По запросу «…» совпадений нет», хотя поиск ещё не запускали — на устройстве это
     * выглядело как «книги нет в библиотеке» (проверено 30.09.2026: «litres» → «Ничего не найдено»,
     * после кнопки «Найти» — 28 книг). Заодно подпись показывает тот термин, который искали,
     * а не тот, что человек набрал после.
     */
    val searchedTerm: String? = null,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: AppText? = null,
)
