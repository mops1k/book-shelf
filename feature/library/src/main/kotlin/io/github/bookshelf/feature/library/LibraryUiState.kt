package io.github.bookshelf.feature.library


import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder

/**
 * Карточка книги на полке (UI-модель).
 *
 * Собирается из доменной `Book` рядом с экраном: экран не должен видеть поля Room-сущности,
 * а сетка не должна зависеть от изменений в `core:data`.
 */
data class BookCardUi(
    /** `books.id` — ключ для навигации и `key` в ленивой сетке. */
    val id: String,
    /** Название книги; при `parseState = FAILED` — заголовок из имени файла. */
    val title: String,
    /** Авторы одной строкой: «Лем, Станислав». */
    val authors: String,
    /** Путь к файлу обложки в кэше; `null` — рисуется заглушка с названием. */
    val coverPath: String? = null,
    /** Формат файла без точки: показывается бейджем в правом верхнем углу обложки. */
    val extension: String = "",
    /** Статус чтения — из `books.readingStatus`. */
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    /** Прогресс чтения в процентах; `null` — читалка его не отдаёт (Sprint 7). */
    val progressPercent: Int? = null,
)

/**
 * Активные чипы фильтров над полкой (UI-модель).
 *
 * Строки, а не доменные сущности: полке нужны только подписи чипов, а фасеты
 * с числами книг считает экран фильтров.
 */
data class FilterChipsUi(
    /** Подписи выбранных авторов. */
    val authors: List<String> = emptyList(),
    /** Подписи выбранных серий. */
    val series: List<String> = emptyList(),
    /** Выбранные форматы: `FB2`, `EPUB`, … */
    val formats: List<String> = emptyList(),
    /** Выбранные статусы чтения. */
    val statuses: List<ReadingStatus> = emptyList(),
    /** Выбранные теги. */
    val tags: List<String> = emptyList(),
) {
    /** Сколько фильтров выбрано — для подписи «Фильтры (2)». */
    val activeCount: Int
        get() = authors.size + series.size + formats.size + statuses.size + tags.size

    /** Есть ли что сбрасывать. */
    val isEmpty: Boolean get() = activeCount == 0
}

/**
 * Подписи активных фильтров из доменного запроса — для кнопки «Фильтры (N)» и подвала полки.
 *
 * Считаются **значения**, а не критерии: три автора и одна серия дают «Фильтры (4)». Так решил
 * Александр 28.09.2026, разбирая, что цифра на кнопке всегда оставалась нулём.
 *
 * Без этой функции `LibraryUiState.activeFilters` не заполнялось нигде, кроме сброса: полка
 * фильтровала книги правильно, а подпись кнопки врала.
 */
fun BookQuery.toFilterChipsUi(): FilterChipsUi = FilterChipsUi(
    authors = authorNames.toList(),
    series = series.toList(),
    formats = formats.toList(),
    statuses = statuses.toList(),
    tags = tags.toList(),
)

/**
 * Состояние экрана полки.
 *
 * На этапе каркаса заполнено только значение по умолчанию: запросов к БД нет.
 * После Sprint 4 [books] и [total] приходят из `BookRepository.page(LibraryQuery)`
 * как `PageResult<Book>`, а сортировка, группировка и фильтры — поля `LibraryQuery`.
 */
data class LibraryUiState(
    /** Книги текущей страницы. */
    val books: List<BookCardUi> = emptyList(),
    /**
     * Показывать ли вход в каталоги OPDS.
     *
     * Сборка без OPDS (`ENABLE_OPDS = false`, ADR-006) не показывает кнопку: маршрута каталогов
     * в ней нет, и кнопка вела бы в никуда.
     */
    val catalogsEnabled: Boolean = true,
    /** Всего книг по текущему запросу (для подписи «Стр. N/M · K книг»). */
    val total: Int = 0,
    /** Номер текущей страницы, нумерация с нуля. */
    val page: Int = 0,
    /** Размер страницы: берётся из плотности интерфейса («E-ink UX» §2.3). */
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    /** Порядок сортировки — доменный enum, чтобы не дублировать его в UI. */
    val sort: SortOrder = SortOrder.AUTHOR,
    /**
     * Направление порядка.
     *
     * Живёт в состоянии, а не только в запросе: второй шаг выбора сортировки показывает, какое
     * направление действует, и его нужно нарисовать.
     */
    val descending: Boolean = false,
    /** Группировка с заголовками-разделителями. */
    val grouping: Grouping = Grouping.NONE,
    /** Чипы активных фильтров над полкой. */
    val activeFilters: FilterChipsUi = FilterChipsUi(),
    /**
     * Порядок задан одной серией: внутри неё сортировка не выбирается, книги идут по выпуску.
     *
     * Хранится отдельно от [activeFilters]: ограничение по серии приходит и с экрана фильтров,
     * и из обзора «по сериям», а подвал обязан показывать настоящий порядок книг в обоих случаях.
     */
    val seriesOrderByRelease: Boolean = false,
    /** Обзор по категориям (авторы/серии) вместо плоского списка книг. */
    val browse: BrowseUi = BrowseUi(),
    /** Идёт загрузка страницы. */
    val isLoading: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: AppText? = null,
) {
    /** Всего страниц при текущем размере страницы. */
    val totalPages: Int get() = pageCount(total, pageSize)

    /** Доступна ли предыдущая страница. */
    val hasPrevious: Boolean get() = hasPreviousPage(page)

    /** Доступна ли следующая страница. */
    val hasNext: Boolean get() = hasNextPage(page, total, pageSize)
}

/**
 * Какое пустое состояние показать на полке.
 *
 * Их два, и они не взаимозаменяемы: «книг нет вовсе — отсканируйте папку» и «книги есть, но
 * ни одна не подходит под фильтр — снимите фильтр». До 28.09.2026 во втором случае предлагалось
 * сканирование, и при активном фильтре это сбивало с толку.
 */
internal enum class ShelfEmptyState {
    /** В библиотеке нет книг вообще. */
    NO_BOOKS,

    /** Книги есть, но отбор их отсекает. */
    FILTERED_OUT,
}

/** Пустое состояние полки или `null`, если книги есть. */
internal fun shelfEmptyState(state: LibraryUiState): ShelfEmptyState? = when {
    state.books.isNotEmpty() -> null
    !state.activeFilters.isEmpty -> ShelfEmptyState.FILTERED_OUT
    else -> ShelfEmptyState.NO_BOOKS
}

/** Режим обзора полки: плоский список книг или категории («E-ink UX» §6.2). */
enum class BrowseMode {
    /** Обычная полка. */
    OFF,

    /** Категории верхнего уровня — авторы. */
    AUTHORS,

    /** Категории верхнего уровня — серии. */
    SERIES,

    /** Категории верхнего уровня — жанры; выбранный жанр раскрывается авторами. */
    GENRES,
}

/**
 * Уровень обзора.
 *
 * Автор — совмещённый экран: его серии идут категориями, а книги вне серий показываются ниже
 * обычным списком/сеткой полки («E-ink UX» §6.2). Так книга без серии находится одним
 * взглядом, без лишнего уровня «Вне серий». Если серий у автора нет, категорий не будет вовсе,
 * и на экране останется тот же список его книг.
 */
sealed interface BrowseLevel {

    /** Список категорий верхнего уровня. */
    data object Root : BrowseLevel

    /** Выбран автор: сверху его серии, снизу — его книги вне серий. */
    data class Author(val name: String) : BrowseLevel

    /**
     * Выбран набор авторов — категория обзора «Авторы».
     *
     * Отдельно от [Author]: набор отвечает на вопрос «книги ровно такого состава авторов»
     * («Александр Тарарев, Юрий Тарарев»), а [Author] приходит из карточки книги и означает
     * «все книги этого человека» — в любой позиции и с любыми соавторами. Совпадают эти два
     * уровня только у книги с одним автором.
     *
     * @param key ключ набора (`authorSetKey`): имена по алфавиту через «|». Он же лежит в
     *   `CategoryItemUi.id`, поэтому «набор» и «имя автора» нигде не смешиваются;
     * @param label подпись по алфавиту — для заголовка уровня.
     */
    data class AuthorSet(val key: String, val label: String) : BrowseLevel

    /** Выбрана серия: показываем её книги. */
    data class Series(val name: String) : BrowseLevel

    /**
     * Выбран жанр: категориями идут авторы этого жанра.
     *
     * @param code код жанра из метаданных (`sf_space`) — он уходит в фильтр запроса;
     * @param label название для человека («Космическая фантастика») — показывается в заголовке.
     */
    data class Genre(val code: String, val label: String) : BrowseLevel
}

/** Строка категории в обзоре: подпись и число книг. */
data class CategoryItemUi(
    /**
     * Значение для фильтра: имя автора, ключ набора авторов или имя серии.
     *
     * У набора авторов это `authorSetKey` — имена по алфавиту через «|»; по нему же строится
     * уровень обзора, поэтому разделитель обязан отличаться от запятой в подписи.
     */
    val id: String,
    /**
     * Значение подписи: имя автора, серии или набор авторов (данные) либо код жанра.
     *
     * Показывается как есть, когда [labelText] нет: имена — это данные, переводить нечего.
     */
    val label: String,
    /**
     * Подпись, выбранная интерфейсом, если значение — код (жанр).
     *
     * У слоя данных ресурсов нет: готовая русская подпись оттуда показывалась бы по-русски и в
     * английском интерфейсе, а код без перевода читается как `sf_space` («TDD» §8).
     */
    val labelText: AppText? = null,
    val count: Int,
)

/**
 * Подпись уровня обзора: ресурс и его аргументы.
 *
 * Готовый текст здесь держать нельзя: слова живут в `res/values` (ru) и `values-en`, а выбор
 * подписи остаётся чистой функцией ViewModel — её проверяют тесты без Compose.
 */
data class BrowseTitleUi(
    val titleRes: Int,
    /**
     * Единственный подставляемый аргумент («Жанр: %1$s») или `null`, если подставлять нечего.
     *
     * Не список: у подписей уровня больше одного аргумента не бывает, а список заставил бы
     * раскрывать массив на каждом вызове `stringResource` (`SpreadOperator` в detekt).
     */
    val arg: String? = null,
    /**
     * Подставляемый текст, если аргумент переводится.
     *
     * Так приходит название жанра: в `arg` лежит код метаданных (`sf_space`), а слова выбирает
     * экран (`genreText`), потому что у слоя данных нет ресурсов («TDD» §8). Имена авторов
     * и серий остаются в [arg] — это данные, их переводить нечем.
     */
    val argText: AppText? = null,
)

/** Состояние обзора полки. */
data class BrowseUi(
    val mode: BrowseMode = BrowseMode.OFF,
    val level: BrowseLevel = BrowseLevel.Root,
    /** Категории текущего уровня; пустой список при уровне «серия» — тогда показываются книги. */
    val categories: List<CategoryItemUi> = emptyList(),
    /** Подпись уровня: «Авторы», «Серии», «Автор: …», «Серия: …». Текст берётся из ресурсов. */
    val title: BrowseTitleUi? = null,
    /** Есть ли куда вернуться (у корня «назад» выключает обзор). */
    val canGoBack: Boolean = false,
) {
    /** Показывать категории вместо книг: только там, где они действительно есть. */
    val showsCategories: Boolean get() = mode != BrowseMode.OFF && categories.isNotEmpty()

    /** Обзор включён хотя бы для одной размерности. */
    val isActive: Boolean get() = mode != BrowseMode.OFF

    /**
     * Показывать ли строку видов полки («Сетка / Авторы / Серии / Жанры»).
     *
     * Только на корне обзора: внутри категории (жанр, автор, серия) человек выбирает книгу, и
     * кнопки смены вида там мешают — вернуться даёт «Назад» в шапке (решение Александра 29.09.2026).
     */
    val showsViewSwitch: Boolean get() = level == BrowseLevel.Root
}
