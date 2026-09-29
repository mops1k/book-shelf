package io.github.bookshelf.feature.filters

import io.github.bookshelf.core.data.model.FacetSet

/**
 * Состояние редактора правил умной полки.
 *
 * [selected] — черновик, а не применённый фильтр библиотеки: пока пользователь не нажал
 * «Сохранить», правила полки остаются прежними, а полка, из которой редактор открыли, —
 * фильтром не тронута.
 */
data class ShelfRulesUiState(
    /** Название полки — в подзаголовке экрана. */
    val shelfName: String = "",
    /** Текущие условия: с них редактор начинается и ими же сохраняется. */
    val selected: FilterSelectionUi = FilterSelectionUi(),
    /** Фасеты с числами книг; `null` — ещё не загружены. */
    val facets: FacetSet? = null,
    /** Сколько книг подходит под текущие условия. */
    val resultCount: Int = 0,
    /** Идёт загрузка фасетов или пересчёт результата. */
    val isLoading: Boolean = false,
    /** Правила сохранены: экран можно закрывать. */
    val saved: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
)
