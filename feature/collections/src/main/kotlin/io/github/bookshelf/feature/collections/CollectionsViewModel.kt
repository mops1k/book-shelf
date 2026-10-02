package io.github.bookshelf.feature.collections

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.fold
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.data.removable.RemovableShelf
import io.github.bookshelf.core.data.removable.RemovableShelfRepository
import io.github.bookshelf.core.data.removable.volumeTreeUri
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import io.github.bookshelf.core.storage.volume.RemovableVolume
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Имя новой полки, пока в экране нет отдельного поля ввода названия. */

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
    /**
     * Полка съёмного носителя: карты памяти и флешки (требование Александра 01.10.2026).
     *
     * Отдельно от [collectionRepository]: полка носителя не лежит в базе полок — она появляется
     * вместе с носителем и исчезает вместе с ним.
     */
    private val removableShelfRepository: RemovableShelfRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())

    /** Состояние экрана коллекций для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    /** Текущая подписка на полки: [onRefresh] перезапускает её, а не заводит вторую. */
    private var collectionsJob: Job? = null

    /**
     * Носитель, к которому относятся действия экрана.
     *
     * Хранится, а не берётся из состояния: в состоянии для экрана лежит только то, что он
     * показывает (метка, число книг), а `RemovableVolume` нужен лишь вызовам репозитория.
     */
    private var cardVolume: RemovableVolume? = null

    init {
        observeCollections()
        observeFilterSelection()
        observeRemovableShelf()
    }

    /** Выделить полку — цель действия «Открыть» с D-pad и аппаратных кнопок. */
    fun onSelect(collectionId: String) {
        _uiState.value = _uiState.value.copy(selectedId = collectionId)
    }

    /**
     * Создать новую ручную полку.
     *
     * Имени пока нет где спросить: кнопка «Создать» вызывает метод без аргументов, поэтому в базу
     * уходит ПУСТОЕ имя, а слова подставляет экран («Новая полка», «Новая полка 2» — номер
     * считается по порядку безымянных полок). Так в базе не остаётся русского текста, который
     * не переводится (docs/tdd.md §8). Когда появится диалог ввода, сюда придёт текст человека
     * (см. [onRename]).
     */
    fun onCreate() {
        val name = ""
        viewModelScope.launch {
            collectionRepository.create(name = name, type = CollectionType.MANUAL)
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError(R.string.collections_error_create, it) },
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
            // Своё сообщение вместо общего: человеку важно, что именно не так с именем.
            _uiState.update { it.copy(error = AppText(R.string.collections_error_rename_empty)) }
            return
        }
        viewModelScope.launch {
            collectionRepository.rename(collectionId = collectionId, name = name.trim())
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError(R.string.collections_error_rename, it) },
                )
        }
    }

    /** Удалить полку (книги при этом остаются в библиотеке). */
    fun onDelete(collectionId: String) {
        viewModelScope.launch {
            collectionRepository.delete(collectionId)
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError(R.string.collections_error_delete, it) },
                )
        }
    }

    /** Настроить правила умной полки; [rulesJson] — в том же формате, что у фильтров. */
    fun onEditRules(collectionId: String, rulesJson: String) {
        if (rulesJson.isBlank()) {
            _uiState.update { it.copy(error = AppText(R.string.collections_error_rules_empty)) }
            return
        }
        viewModelScope.launch {
            collectionRepository.updateSmartRules(collectionId = collectionId, rulesJson = rulesJson)
                .fold(
                    onSuccess = { clearError() },
                    onFailure = { showError(R.string.collections_error_save_rules, it) },
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
        // Имени не пишем: слова собирает экран из правила (docs/tdd.md §8).
        viewModelScope.launch { createSmartShelf(name = "", rules = preset.rules) }
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
            _uiState.update { it.copy(error = AppText(R.string.collections_error_filter_empty)) }
            return
        }
        // Имени у подборки нет: подпись правила собирает экран из частей (`rulesSummary`),
        // и в базе не остаётся русского текста, который не переводится (docs/tdd.md §8).
        viewModelScope.launch { createSmartShelf(name = "", rules = rules) }
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
            showError(R.string.collections_error_create, created.errorOrNull() ?: AppError.Unknown())
            return
        }
        collectionRepository.updateSmartRules(collection.id, rules.toJson()).fold(
            onSuccess = { clearError() },
            onFailure = { showError(R.string.collections_error_save_shelf_rules, it) },
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
        // «Повторить» в баннере относится и к отказу доступа к носителю: повтор — это новое действие.
        _uiState.update { it.copy(cardError = null) }
        observeCollections()
    }

    /**
     * Дать доступ к носителю: сначала прямой путь, иначе — системный выбор папки.
     *
     * Порядок утверждён Александром 01.10.2026. С доступом ко всем файлам источник заводит сам
     * репозиторий, и человек ничего не видит. Без него репозиторий отказывает
     * [AppError.PermissionDenied] — это не ошибка, а просьба открыть выбор папки на корне носителя.
     */
    fun onGrantCardAccess() {
        val volume = cardVolume ?: return
        viewModelScope.launch {
            removableShelfRepository.ensureSource(volume).fold(
                onSuccess = { _uiState.update { it.copy(cardAccessRequest = null, cardError = null) } },
                onFailure = { error -> onCardAccessFailure(volume, error) },
            )
        }
    }

    /**
     * Человек ответил на системный выбор папки.
     *
     * [treeUri] `null` — выбор закрыт без папки: это не ошибка, строка носителя просто продолжает
     * предлагать доступ. Папку проверяет репозиторий: он знает, что относится к этому носителю.
     */
    fun onCardTreePicked(treeUri: String?) {
        val volume = cardVolume ?: return
        _uiState.update { it.copy(cardAccessRequest = null) }
        if (treeUri == null) return
        viewModelScope.launch {
            removableShelfRepository.grantTreeAccess(volume, treeUri).fold(
                onSuccess = { _uiState.update { it.copy(cardError = null) } },
                onFailure = { error -> _uiState.update { it.copy(cardError = error) } },
            )
        }
    }

    /**
     * Отказ доступа: просьба выбрать папку или честная ошибка на экране.
     *
     * Разделение по типу обязательно: «нет доступа ко всем файлам» — обычный ход событий, о котором
     * человеку сообщают не текстом, а открытым диалогом системы, а всё остальное — действительно
     * ошибка (нет права на выбранную папку, отказ провайдера, сбой базы).
     */
    private fun onCardAccessFailure(volume: RemovableVolume, error: AppError) {
        if (error is AppError.PermissionDenied) {
            // Пикер открывается на дереве носителя: искать карту в списке мест вручную — работа.
            _uiState.update { it.copy(cardAccessRequest = volumeTreeUri(volume.uuid), cardError = null) }
        } else {
            _uiState.update { it.copy(cardError = error) }
        }
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
                    showError(R.string.collections_error_load, throwable.toAppError())
                }
                .collect { collections ->
                    val rows = collections.toUiList()
                    _uiState.value = _uiState.value.copy(
                        collections = rows,
                        // «Доступные автополки» — готовые правила, которых в списке ещё нет.
                        // Совпадение по ПРАВИЛАМ, а не по названию: у служебных полок имя пустое
                        // (его собирает экран), а правила — это и есть то, чем полка отличается.
                        presets = SmartPreset.entries
                            .filter { preset ->
                                collections.none { row ->
                                    SmartRules.fromJson(row.rulesJson) == preset.rules
                                }
                            }
                            .map { preset -> preset.toUi() },
                        isLoading = false,
                    )
                }
        }
    }

    /**
     * Подписаться на полку съёмного носителя.
     *
     * Носитель вставили — строка появится; вынули или выключили настройку — исчезнет: состав
     * решает репозиторий, экран показывает то, что пришло. Отказ потока не гасит список полок:
     * без полки носителя экран остаётся рабочим, поэтому в состояние кладётся тип ошибки, а текст
     * выберет экран.
     */
    private fun observeRemovableShelf() {
        viewModelScope.launch {
            removableShelfRepository.observeShelf()
                .catch { throwable ->
                    cardVolume = null
                    _uiState.update {
                        it.copy(removableShelf = null, cardError = throwable.toAppError())
                    }
                }
                .collect { shelf ->
                    cardVolume = shelf?.volume
                    // Успешно заведённый источник приходит сюда же: строка перестаёт просить доступ.
                    _uiState.update { it.copy(removableShelf = shelf?.toUi(), cardError = null) }
                }
        }
    }


    /**
     * Показать ошибку действия: что не получилось (ресурс экрана) и почему (текст по типу [AppError]).
     *
     * Оба текста — ресурсы, поэтому сообщение переводится целиком; готовой строки в состоянии нет
     * (docs/tdd.md §5), а `AppError.message` человеку не показывается (§7) — он остаётся диагностикой.
     */
    private fun showError(actionRes: Int, error: AppError) {
        _uiState.update { it.copy(error = AppText(actionRes, error.text())) }
    }

    /** Убрать баннер ошибки после успешного действия. */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Полка для списка экрана.
     *
     * Правила умной полки приходят JSON-ом, а разборщика правил в контракте нет, поэтому вместо
     * длинного JSON в подписи стоит короткая отметка: разбор шаблонов появится вместе с редактором.
     */
    /**
     * Полки для экрана.
     *
     * Безымянным полкам номер подставляется по порядку: в базе у них пустое имя, а «Новая полка»
     * и «Новая полка 2» собирает экран. Номер считается по уже загруженному списку, чтобы в строке
     * не было двух одинаковых названий.
     */
    private fun List<Collection>.toUiList(): List<CollectionUi> {
        var unnamedSeen = 0
        return map { collection ->
            val number = if (collection.name.isBlank()) {
                unnamedSeen += 1
                unnamedSeen
            } else {
                null
            }
            collection.toUi(defaultNumber = number)
        }
    }

    private fun Collection.toUi(defaultNumber: Int? = null): CollectionUi = CollectionUi(
        id = id,
        name = name,
        bookCount = bookCount,
        isSmart = type == CollectionType.SMART,
        // Подпись правила читается человеком, но слова выбирает экран: слой данных отдаёт части
        // правила. В строке списка показывается одно условие — с двумя подпись обрывалась.
        rulesSummary = SmartRules.fromJson(rulesJson)
            ?.takeIf { !it.isEmpty }
            ?.parts()
            ?.toSummaryText(maxParts = ROW_RULES_PARTS),
        defaultTitle = defaultNumber?.let { number -> defaultCollectionTitle(number) },
        previewCovers = previewCovers,
    )

    /** Подпись безымянной полки: первая — «Новая полка», следующие — с номером. */
    private fun defaultCollectionTitle(number: Int): AppText = if (number <= 1) {
        AppText(R.string.collections_default_name)
    } else {
        AppText(R.string.collections_default_name_numbered, number)
    }

    /**
     * Полка носителя для экрана.
     *
     * Метка тома — данные системы, а не переводимый текст: пустую метку экран заменяет названием
     * по умолчанию («Карта памяти»), поэтому сюда попадает как есть.
     */
    private fun RemovableShelf.toUi(): RemovableShelfUi = RemovableShelfUi(
        label = volume.label,
        bookCount = bookCount,
        needsAccess = needsAccess,
    )

    /**
     * Готовые умные полки.
     *
     * Пользователю не нужно собирать очевидное вручную: «Избранное» и статусы чтения — это одно
     * правило, а не набор галочек.
     */
    private enum class SmartPreset(
        val id: String,
        /** Название полки на экране — ресурс; в базу имя не пишется (docs/tdd.md §8). */
        @StringRes val titleRes: Int,
        /** Подпись правила на экране — ресурс. */
        @StringRes val hintRes: Int,
        val rules: SmartRules,
    ) {
        FAVORITES(
            "favorites",
            R.string.collections_preset_favorites,
            R.string.collections_preset_favorites_hint,
            SmartRules(favoritesOnly = true),
        ),
        UNREAD(
            "unread",
            R.string.collections_preset_unread,
            R.string.collections_preset_unread_hint,
            SmartRules(statuses = setOf(ReadingStatus.UNREAD.name)),
        ),
        READING(
            "reading",
            R.string.collections_preset_reading,
            R.string.collections_preset_reading_hint,
            SmartRules(statuses = setOf(ReadingStatus.READING.name)),
        ),
        READ(
            "read",
            R.string.collections_preset_read,
            R.string.collections_preset_read_hint,
            SmartRules(statuses = setOf(ReadingStatus.READ.name)),
        );

        fun toUi(): SmartPresetUi = SmartPresetUi(id = id, titleRes = titleRes, hint = AppText(hintRes))
    }
}
