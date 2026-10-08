package io.github.bookshelf.core.data.mapper

import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.datastore.model.Grouping as SettingsGrouping
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.SortOrder as SettingsSortOrder

/**
 * Порядок полки по умолчанию, каким его понимает слой данных (`LibraryQuery`).
 *
 * Отдельная модель, а не сразу `LibraryQuery`: у запроса есть ещё страница, размер страницы и
 * фильтры, которые к настройке «чем открывается полка» отношения не имеют. Так функция маппинга
 * остаётся чистой и её можно проверить без БД и Android.
 */
data class LibraryQueryDefaults(
    /** Поле сортировки по умолчанию. */
    val sort: SortOrder,
    /** Обратный порядок для выбранного поля. */
    val descending: Boolean,
    /** Признак, по которому режется страница, или [Grouping.NONE]. */
    val grouping: Grouping,
)

/**
 * Настройка «Полка» → [LibraryQueryDefaults] («E-ink UX» §6.2).
 *
 * Перечисления в настройках и в слое данных разные намеренно: `:core:data` не должен зависеть от
 * того, где лежит настройка, а `:core:datastore` — от модели запроса (об этом же сказано в
 * `core/data/model/SortOrder.kt`). Маппинг собирается здесь, а не в `:feature:library`: порядок
 * по умолчанию — свойство запроса к библиотеке, а не экрана.
 *
 * Соответствие задано явным `when` без `else`: если в настройках появится новое значение
 * сортировки или группировки, компиляция упадёт здесь, а не покажет читателю тихо неверный
 * порядок полки. Обе пары перечислений сегодня совпадают по составу, поэтому «непоказываемых»
 * вариантов нет — все поддерживаются запросом (`BookPageSql.orderBy`).
 */
fun LibrarySettings.toLibraryQueryDefaults(): LibraryQueryDefaults = LibraryQueryDefaults(
    sort = sortOrder.toQuerySortOrder(),
    descending = sortDescending,
    grouping = grouping.toQueryGrouping(),
)

/** Сортировка из настроек → сортировка запроса полки. */
fun SettingsSortOrder.toQuerySortOrder(): SortOrder = when (this) {
    SettingsSortOrder.TITLE -> SortOrder.TITLE
    SettingsSortOrder.AUTHOR -> SortOrder.AUTHOR
    SettingsSortOrder.SERIES -> SortOrder.SERIES
    SettingsSortOrder.ADDED_AT -> SortOrder.ADDED_AT
    SettingsSortOrder.LAST_OPENED -> SortOrder.LAST_OPENED
    SettingsSortOrder.SIZE -> SortOrder.SIZE
    SettingsSortOrder.PROGRESS -> SortOrder.PROGRESS
    SettingsSortOrder.YEAR -> SortOrder.YEAR
}

/** Группировка из настроек → группировка запроса полки. */
fun SettingsGrouping.toQueryGrouping(): Grouping = when (this) {
    SettingsGrouping.NONE -> Grouping.NONE
    SettingsGrouping.SERIES -> Grouping.SERIES
    SettingsGrouping.AUTHOR -> Grouping.AUTHOR
    SettingsGrouping.FOLDER -> Grouping.FOLDER
    SettingsGrouping.FORMAT -> Grouping.FORMAT
}

/**
 * Сортировка запроса полки → сортировка в настройках.
 *
 * Обратный маппинг нужен с 08.10.2026: выбор, сделанный на полке, пишется в настройки
 * («Сортировка по умолчанию»). Без него после перезапуска приложения полка возвращалась к
 * прежнему умолчанию, и пользователь видел не тот порядок, который выбирал. `when` без `else` по
 * той же причине, что и в прямом маппинге: новое значение упадёт на компиляции здесь, а не
 * покажет тихо чужой порядок книг.
 */
fun SortOrder.toSettingsSortOrder(): SettingsSortOrder = when (this) {
    SortOrder.TITLE -> SettingsSortOrder.TITLE
    SortOrder.AUTHOR -> SettingsSortOrder.AUTHOR
    SortOrder.SERIES -> SettingsSortOrder.SERIES
    SortOrder.ADDED_AT -> SettingsSortOrder.ADDED_AT
    SortOrder.LAST_OPENED -> SettingsSortOrder.LAST_OPENED
    SortOrder.SIZE -> SettingsSortOrder.SIZE
    SortOrder.PROGRESS -> SettingsSortOrder.PROGRESS
    SortOrder.YEAR -> SettingsSortOrder.YEAR
}

/** Группировка запроса полки → группировка в настройках. */
fun Grouping.toSettingsGrouping(): SettingsGrouping = when (this) {
    Grouping.NONE -> SettingsGrouping.NONE
    Grouping.SERIES -> SettingsGrouping.SERIES
    Grouping.AUTHOR -> SettingsGrouping.AUTHOR
    Grouping.FOLDER -> SettingsGrouping.FOLDER
    Grouping.FORMAT -> SettingsGrouping.FORMAT
}
