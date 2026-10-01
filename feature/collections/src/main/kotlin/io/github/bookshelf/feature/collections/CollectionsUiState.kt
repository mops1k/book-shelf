package io.github.bookshelf.feature.collections

import io.github.bookshelf.core.common.error.AppError

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
    /** Пути обложек первых книг — строка полки показывает их вместо иконки. */
    val previewCovers: List<String> = emptyList(),
)

/** Готовая умная полка, которую пользователю остаётся добавить («Доступные автополки»). */
data class SmartPresetUi(
    /** Ключ пресета для `CollectionsViewModel.onAddPreset`. */
    val id: String,
    /** Название полки, которая появится после добавления. */
    val title: String,
    /** Подпись правила: «Статус чтения: не прочитано». */
    val hint: String,
)

/**
 * Полка съёмного носителя в списке полок (требование Александра 01.10.2026).
 *
 * Это не полка из базы: она появляется и исчезает вместе с носителем, а её состав — книги источника
 * носителя. [label] — данные системы (метка тома), а не переводимый текст: пустая метка означает,
 * что экран показывает название по умолчанию.
 */
data class RemovableShelfUi(
    /** Метка носителя от системы: «SD-карта», «USB-накопитель». */
    val label: String,
    /** Книг носителя в библиотеке. */
    val bookCount: Int,
    /** Источник носителя ещё не заведён: экран предлагает дать доступ. */
    val needsAccess: Boolean,
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
    /** Готовые умные полки, которых в списке ещё нет. */
    val presets: List<SmartPresetUi> = emptyList(),
    /** Активна ли кнопка «Сохранить текущий фильтр как полку». */
    val canSaveCurrentFilter: Boolean = false,
    /** Идёт загрузка списка. */
    val isLoading: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
    /** Полка носителя; `null` — носителя нет или полка выключена настройкой. */
    val removableShelf: RemovableShelfUi? = null,
    /**
     * Адрес дерева, с которого система откроет выбор папки носителя; `null` — выбор не запрошен.
     *
     * Состояние, а не разовое событие: экран открывает пикер на каждое новое значение и не может
     * пропустить его при пересоздании (поворот экрана, возврат из фона). После ответа человека —
     * выбора или отказа — значение сбрасывается в `null`.
     */
    val cardAccessRequest: String? = null,
    /**
     * Отказ доступа к носителю.
     *
     * Хранится типом [AppError], а не готовым текстом: строку выбирает экран ресурсом (правило
     * локализации), и один тип ошибки даёт один переводимый текст.
     */
    val cardError: AppError? = null,
)
