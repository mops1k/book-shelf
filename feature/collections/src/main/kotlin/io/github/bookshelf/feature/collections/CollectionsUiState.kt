package io.github.bookshelf.feature.collections

/**
 * Полка в списке коллекций (UI-модель).
 *
 * Ручные и умные полки показываются одним списком: различает их [isSmart] и подпись правил,
 * а не отдельная вкладка (docs/eink-ux.md §6.3).
 */
data class CollectionUi(
    /** `collections.id`. */
    val id: String,
    /** Название, заданное пользователем. */
    val name: String,
    /** Число книг в полке. */
    val bookCount: Int,
    /** true — умная полка, вычисляемая по правилам. */
    val isSmart: Boolean = false,
    /** Человекочитаемая подпись правил: «статус: читаю, формат: FB2». */
    val rulesSummary: String? = null,
)

/**
 * Состояние экрана коллекций.
 *
 * На этапе каркаса заполнено значением по умолчанию: запросов к БД нет. После Sprint 7
 * [collections] приходит из `CollectionRepository.observeAll()`, а умные полки считаются
 * тем же предикатом, что и фильтры (docs/decisions.md ADR-008).
 */
data class CollectionsUiState(
    /** Ручные и умные полки. */
    val collections: List<CollectionUi> = emptyList(),
    /** Выделенная полка — цель действия «Открыть» с D-pad и аппаратных кнопок. */
    val selectedId: String? = null,
    /** Id полки, которую сейчас переименовывают; `null` — обычный режим. */
    val renamingId: String? = null,
    /** Текст в поле переименования. */
    val renameDraft: String = "",
    /** Идёт загрузка списка. */
    val isLoading: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)
