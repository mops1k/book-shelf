package io.github.bookshelf.feature.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.fold
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.FilterSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Имя новой полки, пока в экране нет отдельного поля ввода названия. */
private const val DEFAULT_COLLECTION_NAME = "Новая полка"

/**
 * Сколько условий правила показывать в строке полки.
 *
 * Одно: при двух подпись «Умная полка · жанр: Космическая фантастика, статус: не прочитано»
 * не влезает в строку списка и обрывается многоточием на середине названия жанра. Остальные
 * условия видны в редакторе правил — там места достаточно.
 */
private const val ROW_RULES_PARTS = 1

/**
 * ViewModel коллекций (docs/eink-ux.md §6.3, Sprint 7).
 *
 * Список полок живёт по подписке на [CollectionRepository.observeCollections] в [viewModelScope]:
 * успешные create/rename/delete/обновление правил не перечитывают базу, потому что новое
 * значение присылает сам Flow репозитория. Ошибка любого действия кладётся в
 * [CollectionsUiState.error] понятным текстом — наружу исключения не пробрасываются.
 *
 * [bookRepository] пока не используется: в [CollectionsUiState] нет списка книг полки, а
 * `observeBooks` нужен именно этому экрану.
 */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    /**
     * Общий выбор фильтров полки.
     *
     * Нужен кнопке «Сохранить текущий фильтр как полку»: правила умной полки — это тот же набор
     * условий, что у фильтра, и второй способ их собирать разошёлся бы с первым.
     */
    private val filterSelection: FilterSelection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())

    /** Состояние экрана коллекций для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    /** Текущая подписка на полки: [onRefresh] перезапускает её, а не заводит вторую. */
    private var collectionsJob: Job? = null

    init {
        observeCollections()
        observeFilterSelection()
    }

    /** Выделить полку — цель действия «Открыть» с D-pad и аппаратных кнопок. */
    fun onSelect(collectionId: String) {
        _uiState.value = _uiState.value.copy(selectedId = collectionId)
    }

    /**
     * Создать новую ручную полку.
     *
     * Названия пока нет где спросить: кнопка «Создать» вызывает метод без аргументов, поэтому
     * берётся [DEFAULT_COLLECTION_NAME] с номером, если такая полка уже есть. Когда появится
     * диалог ввода, сюда должен прийти текст пользователя (см. [onRename]).
     */
    fun onCreate() {
        val name = nextCollectionName()
        viewModelScope.launch {
            collectionRepository.create(name = name, type = CollectionType.MANUAL)
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError("Не удалось создать полку", it) },
                )
        }
    }

    /** Переименовать выделенную полку; [name] — новое название из диалога переименования. */
    /** Перейти к переименованию: экран показывает поле с текущим названием. */
    fun onStartRename(collection: CollectionUi) {
        _uiState.update { it.copy(renamingId = collection.id, renameDraft = collection.name) }
    }

    /** Отменить переименование, ничего не меняя. */
    fun onCancelRename() {
        _uiState.update { it.copy(renamingId = null, renameDraft = "") }
    }

    /** Ввод нового названия. */
    fun onRenameDraftChanged(value: String) {
        _uiState.update { it.copy(renameDraft = value) }
    }

    /** Сохранить новое название и выйти из режима переименования. */
    fun onCommitRename() {
        val renamingId = _uiState.value.renamingId ?: return
        onRename(renamingId, _uiState.value.renameDraft)
        _uiState.update { it.copy(renamingId = null, renameDraft = "") }
    }

    fun onRename(collectionId: String, name: String) {
        if (name.isBlank()) {
            showError("Не удалось переименовать полку", AppError.InvalidArgument("name", "имя не должно быть пустым"))
            return
        }
        viewModelScope.launch {
            collectionRepository.rename(collectionId = collectionId, name = name.trim())
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError("Не удалось переименовать полку", it) },
                )
        }
    }

    /** Удалить полку (книги при этом остаются в библиотеке). */
    fun onDelete(collectionId: String) {
        viewModelScope.launch {
            collectionRepository.delete(collectionId)
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError("Не удалось удалить полку", it) },
                )
        }
    }

    /** Настроить правила умной полки; [rulesJson] — в том же формате, что у фильтров. */
    fun onEditRules(collectionId: String, rulesJson: String) {
        if (rulesJson.isBlank()) {
            showError(
                "Не удалось сохранить правила",
                AppError.InvalidArgument("rulesJson", "правила не должны быть пустыми"),
            )
            return
        }
        viewModelScope.launch {
            collectionRepository.updateSmartRules(collectionId = collectionId, rulesJson = rulesJson)
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError("Не удалось сохранить правила", it) },
                )
        }
    }

    /**
     * Добавить готовую умную полку («Доступные автополки»).
     *
     * Два действия подряд — создать полку и записать её правила: `create` не принимает правила,
     * потому что ручной полке они не нужны, а умная без правил пуста.
     */
    fun onAddPreset(presetId: String) {
        val preset = SmartPreset.entries.firstOrNull { it.id == presetId } ?: return
        viewModelScope.launch { createSmartShelf(preset.title, preset.rules) }
    }

    /**
     * Сохранить текущий фильтр полки как умную полку.
     *
     * Мост между фильтрами и полками: набор условий у них один и тот же, поэтому пользователю не
     * нужно выставлять те же галочки второй раз.
     */
    fun onSaveCurrentFilter() {
        val query = filterSelection.query.value
        val rules = SmartRules.fromQuery(query)
        if (rules.isEmpty) {
            showError(
                "Не удалось сохранить подборку",
                AppError.InvalidArgument("filter", "сначала выберите фильтры на полке"),
            )
            return
        }
        viewModelScope.launch { createSmartShelf(rules.defaultName(), rules) }
    }

    /**
     * Создать умную полку и сразу записать её правила.
     *
     * Два шага, а не один: `create` правил не принимает — ручной полке они не нужны, а умная без
     * правил пуста. Ошибка первого шага отменяет второй: полка без правил показывала бы «0 книг».
     */
    private suspend fun createSmartShelf(name: String, rules: SmartRules) {
        val created = collectionRepository.create(name, CollectionType.SMART)
        val collection = created.getOrNull()
        if (collection == null) {
            showError("Не удалось создать полку", created.errorOrNull() ?: AppError.Unknown())
            return
        }
        collectionRepository.updateSmartRules(collection.id, rules.toJson()).fold(
            onSuccess = { clearError() },
            onFailure = { showError("Не удалось сохранить правила полки", it) },
        )
    }

    /**
     * Кнопка «Сохранить текущий фильтр как полку» активна, только когда фильтр что-то отсекает.
     */
    private fun observeFilterSelection() {
        viewModelScope.launch {
            filterSelection.query.collect { query ->
                _uiState.value = _uiState.value.copy(
                    canSaveCurrentFilter = !SmartRules.fromQuery(query).isEmpty,
                )
            }
        }
    }

    /**
     * Перечитать список полок по кнопке «Повторить» в баннере ошибки.
     *
     * Обычные действия дублировать не нужно: Flow обновляет состояние сам, поэтому здесь только
     * перезапуск подписки, которая ранее упала.
     */
    fun onRefresh() {
        observeCollections()
    }

    /**
     * Подписаться на полки.
     *
     * Подписка одна на ViewModel: ошибка потока гасит collector, а [onRefresh] поднимает новый.
     */
    private fun observeCollections() {
        collectionsJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        collectionsJob = viewModelScope.launch {
            collectionRepository.observeCollections()
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    showError("Не удалось загрузить список полок", throwable.toAppError())
                }
                .collect { collections ->
                    val rows = collections.map { it.toUi() }
                    _uiState.value = _uiState.value.copy(
                        collections = rows,
                        // «Доступные автополки» — готовые правила, которых в списке ещё нет.
                        // Совпадение по названию: отдельного признака «пресет уже добавлен» в
                        // модели полки нет, а плодить его ради подсказки не стоит.
                        presets = SmartPreset.entries
                            .filter { preset -> rows.none { row -> row.name == preset.title } }
                            .map { preset -> preset.toUi() },
                        isLoading = false,
                    )
                }
        }
    }

    /**
     * Имя для новой полки: «Новая полка», «Новая полка 2» и так далее.
     *
     * Номер подбирается по уже загруженному списку, чтобы в списке не было двух одинаковых названий.
     */
    private fun nextCollectionName(): String {
        val existing = _uiState.value.collections.map { it.name }.toSet()
        if (DEFAULT_COLLECTION_NAME !in existing) return DEFAULT_COLLECTION_NAME
        var number = 2
        while ("$DEFAULT_COLLECTION_NAME $number" in existing) number += 1
        return "$DEFAULT_COLLECTION_NAME $number"
    }

    /** Показать ошибку действия: что не получилось и почему, в терминах [AppError]. */
    private fun showError(action: String, error: AppError) {
        _uiState.value = _uiState.value.copy(error = "$action: ${error.uiText()}")
    }

    /** Убрать баннер ошибки после успешного действия. */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Человекочитаемый текст ошибки.
     *
     * [AppError.message] пуст у части ошибок, поэтому для них берётся описание по типу — иначе
     * пользователь увидит пустой баннер.
     */
    private fun AppError.uiText(): String = message ?: when (this) {
        is AppError.Unsupported -> "пока не поддерживается ($what)"
        is AppError.NotFound -> "не найдено: $what"
        is AppError.InvalidArgument -> "неверное значение: $what"
        is AppError.PermissionDenied -> "нет доступа"
        is AppError.Io -> "ошибка ввода-вывода"
        is AppError.Parse -> "не удалось разобрать данные: $format"
        is AppError.Network -> "ошибка сети" + (httpCode?.let { " (HTTP $it)" } ?: "")
        is AppError.Unauthorized -> "нужна авторизация"
        AppError.AccessLost -> "доступ к источнику потерян"
        is AppError.Unknown -> "неизвестная ошибка"
    }

    /**
     * Полка для списка экрана.
     *
     * Правила умной полки приходят JSON-ом, а разборщика правил в контракте нет, поэтому вместо
     * длинного JSON в подписи стоит короткая отметка: разбор шаблонов появится вместе с редактором.
     */
    private fun Collection.toUi(): CollectionUi = CollectionUi(
        id = id,
        name = name,
        bookCount = bookCount,
        isSmart = type == CollectionType.SMART,
        // Подпись правила читается человеком: «жанр: Космическая фантастика», а не «правила заданы».
        // В строке списка показывается одно условие: с двумя подпись обрывалась многоточием.
        rulesSummary = SmartRules.fromJson(rulesJson)
            ?.takeIf { !it.isEmpty }
            ?.describe(maxParts = ROW_RULES_PARTS),
        previewCovers = previewCovers,
    )

    /** Имя подборки, собранной из текущего фильтра: по нему видно, что внутри. */
    private fun SmartRules.defaultName(): String = "Подборка: ${describe()}"

    /**
     * Готовые умные полки.
     *
     * Пользователю не нужно собирать очевидное вручную: «Избранное» и статусы чтения — это одно
     * правило, а не набор галочек.
     */
    private enum class SmartPreset(
        val id: String,
        val title: String,
        val hint: String,
        val rules: SmartRules,
    ) {
        FAVORITES("favorites", "Избранное", "Книги, отмеченные избранными", SmartRules(favoritesOnly = true)),
        UNREAD(
            "unread",
            "Непрочитанные",
            "Статус чтения: не прочитано",
            SmartRules(statuses = setOf(ReadingStatus.UNREAD.name)),
        ),
        READING(
            "reading",
            "Читаю сейчас",
            "Статус чтения: читаю",
            SmartRules(statuses = setOf(ReadingStatus.READING.name)),
        ),
        READ(
            "read",
            "Прочитанные",
            "Статус чтения: прочитано",
            SmartRules(statuses = setOf(ReadingStatus.READ.name)),
        );

        fun toUi(): SmartPresetUi = SmartPresetUi(id = id, title = title, hint = hint)
    }
}
