package io.github.bookshelf.feature.catalogs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.fold
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.repository.OpdsRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Папка-получатель загрузки (UI-модель). */
data class FolderOptionUi(
    /** `sources.id`. */
    val id: String,
    /** Имя папки. */
    val name: String,
)

/** Подписи уровней, если каталог не дал ни имени автора, ни названия серии. */

/** Длина даты в ISO-8601 (`2026-09-28`): время и часовой пояс в карточке не нужны. */
private const val ISO_DATE_LENGTH = 10

/** Сколько частей в дате ISO-8601: год, месяц, день. */
private const val ISO_DATE_PARTS = 3

/**
 * Дата обновления записи из ISO-8601 в короткую подпись «28.09.2026».
 *
 * Каталоги отдают `2026-09-28T19:01:51+02:00`, и показывать это в карточке книги — значит
 * показывать машинную строку вместо даты. Непонятный формат возвращается как есть: лучше
 * необработанная дата, чем выдуманная.
 */
private fun updatedLabel(iso: String): String {
    val parts = iso.take(ISO_DATE_LENGTH).split('-')
    if (parts.size != ISO_DATE_PARTS || parts.any { part -> part.isEmpty() }) return iso
    return "${parts[2]}.${parts[1]}.${parts[0]}"
}

/** Состояние карточки записи каталога. */
data class EntryDetailsUiState(
    /** Название; пустое — запись не найдена в фиде. */
    val title: String = "",
    /** Авторы одной строкой. */
    val authors: String = "",
    /**
     * Фид автора из записи; `null` — каталог его не объявил.
     *
     * По нему открываются книги и серии автора: искать его поиском после прочтения карточки —
     * лишняя работа, которую каталог уже сделал за нас.
     */
    val authorUrl: String? = null,
    /** Серия; `null` — каталог её не сообщает. */
    val seriesName: String? = null,
    /** Фид серии: открывает книги именно этой серии. */
    val seriesUrl: String? = null,
    /** Описание из `<content type="text">` — у Флибусты именно там, а не в `<summary>`. */
    val summary: String? = null,
    /** Адрес обложки из фида. */
    val coverUrl: String? = null,
    /** Форматы: «FB2, EPUB, MOBI». */
    val formatsLabel: String = "",
    /** Подпись размера файла. */
    val sizeLabel: CatalogsText? = null,
    /** Язык записи. */
    val language: String? = null,
    /** Год издания (`dc:issued`). */
    val issued: String? = null,
    /** Жанры одной строкой (`category`). */
    val genresLabel: String? = null,
    /** Дата обновления записи. */
    val updated: String? = null,
    /** Есть ли прямая ссылка на файл. */
    val canDownload: Boolean = false,
    /** Папки, куда можно сохранить книгу; пусто — папок в приложении нет. */
    val folders: List<FolderOptionUi> = emptyList(),
    /** Выбранная папка; `null` — ещё не выбрана. */
    val selectedFolderId: String? = null,
    /** Идёт загрузка. */
    val isLoading: Boolean = false,
    /** Состояние загрузки этой записи; `null` — книгу ещё не качали. */
    val download: EntryDownloadUi? = null,
    /** Текст ошибки. */
    val error: AppText? = null,
) {
    /**
     * Можно нажать «Скачать».
     *
     * Вторая загрузка той же книги не запускается: пока идёт первая, кнопка занята состоянием —
     * иначе пользователь поставил бы в очередь дубликат, ничего не заметив.
     */
    val canStartDownload: Boolean
        get() = canDownload && download?.isActive != true
}

/**
 * ViewModel карточки записи каталога.
 *
 * Запись берётся из того, что пользователь видел в списке ([loadedEntries]): список догружает
 * страницы 2, 3…, и книга может лежать не на первой странице — повторное чтение первой страницы
 * объявляло её «больше не входящей в фид». Чтение фида остаётся запасным путём: оно нужно, когда
 * записи в памяти нет (например, процесс был перезапущен).
 */
@HiltViewModel
class EntryDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val opdsRepository: OpdsRepository,
    private val sourceRepository: SourceRepository,
    /** Просьба к браузеру каталога: открыть фид автора или серии. */
    private val catalogFeedRequest: CatalogFeedRequest,
    /** Записи, которые показал браузер каталога: из них карточка берёт книгу. */
    private val loadedEntries: LoadedFeedEntries,
) : ViewModel() {

    private val catalogId: String =
        savedStateHandle.get<String>(EntryRoute.ARG_CATALOG_ID).orEmpty()
    private val entryId: String =
        savedStateHandle.get<String>(EntryRoute.ARG_ENTRY_ID).orEmpty()
    private val feedUrl: String =
        savedStateHandle.get<String>(EntryRoute.ARG_FEED_URL).orEmpty()

    private val _uiState = MutableStateFlow(EntryDetailsUiState())

    /** Найденная запись целиком: очередь ставится по объекту, а не по id. */
    private var entry: OpdsEntry? = null

    /** Состояние карточки для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<EntryDetailsUiState> = _uiState.asStateFlow()

    init {
        onRefresh()
        observeFolders()
        observeDownload()
    }

    /**
     * Состояние загрузки этой записи.
     *
     * Нужно, чтобы после нажатия «Скачать» экран не закрывался молча: строка «Загрузка началась»
     * с прогрессом остаётся на месте, а не исчезает вместе с экраном (жалоба Александра,
     * 28.09.2026: «визуально там, где я нахожусь, нет никакого отображения, что началась загрузка»).
     */
    private fun observeDownload() {
        if (entryId.isEmpty()) return
        viewModelScope.launch {
            opdsRepository.observeDownloads()
                .catch { throwable ->
                    _uiState.update { state ->
                        state.copy(error = AppText(
                            R.string.catalogs_entry_error_load_state,
                            throwable.toAppError().text(),
                        ))
                    }
                }
                .collect { downloads ->
                    val mapped = downloads.map { it.toDownloadUi() }
                    _uiState.update { state ->
                        state.copy(download = entryDownloadOf(entryId, mapped))
                    }
                }
        }
    }

    /**
     * Папки-получатели.
     *
     * Загрузке папка нужна обязательно, и без выбора она падала с «выберите папку» — при том,
     * что папка в приложении была ровно одна. Теперь список живой, а при единственной папке
     * она выбирается сразу.
     */
    private fun observeFolders() {
        viewModelScope.launch {
            sourceRepository.observeSources().collect { sources ->
                val enabled = sources.filter { it.enabled }
                _uiState.update { state ->
                    state.copy(
                        folders = enabled.map { source ->
                            FolderOptionUi(id = source.id, name = source.displayName)
                        },
                        selectedFolderId = state.selectedFolderId
                            ?: enabled.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    /** Выбрать папку-получатель. */
    fun onSelectFolder(sourceId: String) {
        _uiState.update { it.copy(selectedFolderId = sourceId, error = null) }
    }

    /**
     * Перейти к книгам автора этой записи.
     *
     * Просьбу забирает браузер каталога: он остался ниже в стеке навигации и помнит свои уровни,
     * поэтому возврат из фида автора ведёт на прежний фид, а не в список каталогов.
     */
    fun onOpenAuthor() {
        val target = entry ?: return
        val url = target.authorUrl ?: return
        // Имя автора — данные; если сервер его не дал, слова «Автор» выбирает приложение.
        val title = target.authorsText
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let(FeedLabel::Named)
            ?: FeedLabel.Message(AppText(R.string.catalogs_level_author))
        catalogFeedRequest.open(catalogId, url, title)
    }

    /** Перейти к книгам серии этой записи. */
    fun onOpenSeries() {
        val target = entry ?: return
        val url = target.seriesUrl ?: return
        val title = target.seriesName
            ?.let(FeedLabel::Named)
            ?: FeedLabel.Message(AppText(R.string.catalogs_level_series))
        catalogFeedRequest.open(catalogId, url, title)
    }

    /**
     * Показать карточку: запись из показанного списка, а чтение фида — запасной путь.
     *
     * Основной путь — [loadedEntries]: в карточке должна открываться та книга, которую пользователь
     * видит в списке. Фид перечитывается только тогда, когда записи в памяти нет (процесс
     * перезапущен, карточка открыта из восстановленного стека).
     */
    fun onRefresh() {
        if (catalogId.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = AppText(R.string.catalogs_entry_error_no_catalog),
                )
            }
            return
        }
        val remembered = loadedEntries.find(catalogId, entryId)
        if (remembered != null) {
            show(remembered)
            return
        }
        // Флаг ставится до запуска корутины: иначе первый кадр показывал пустое название, и экран
        // успевал сказать «Запись не найдена» раньше, чем начиналось чтение фида.
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = opdsRepository.browse(catalogId, feedUrl.takeIf { it.isNotBlank() })
            result.fold(
                onSuccess = { entries ->
                    val found = entries.firstOrNull { it.id == entryId }
                    if (found == null) {
                        entry = null
                        _uiState.value = EntryDetailsUiState(
                            error = AppText(R.string.catalogs_entry_error_not_in_feed),
                        )
                    } else {
                        show(found)
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = AppText(
                            R.string.catalogs_entry_error_load,
                            it.text(),
                        ))
                    }
                },
            )
        }
    }

    /**
     * Заполнить состояние карточки найденной записью.
     *
     * Метаданные берутся из фида целиком: раньше здесь стояли null-ы, и карточка показывала только
     * автора и формат, хотя фид прислал и описание, и серию, и язык, и год. Список папок при этом
     * не теряется: он приходит отдельной подпиской, а состояние перезаписывается целиком — иначе
     * карточка показывала «Папка не выбрана», хотя папка в приложении есть.
     */
    private fun show(found: OpdsEntry) {
        entry = found
        _uiState.value = EntryDetailsUiState(
            folders = _uiState.value.folders,
            selectedFolderId = _uiState.value.selectedFolderId,
            title = found.title,
            authors = found.authorsText.orEmpty(),
            authorUrl = found.authorUrl,
            seriesName = found.seriesName,
            seriesUrl = found.seriesUrl,
            summary = found.summary,
            coverUrl = found.coverUrl,
            formatsLabel = found.formats.joinToString(separator = ", "),
            sizeLabel = found.expectedSize?.let(::fileSizeLabel),
            language = found.language,
            issued = found.issued,
            genresLabel = found.genres.joinToString(separator = ", ").takeIf { it.isNotBlank() },
            updated = found.updated?.let(::updatedLabel),
            canDownload = !found.acquisitionUrl.isNullOrBlank(),
            isLoading = false,
        )
    }

    /**
     * Поставить книгу в очередь.
     *
     * Экран закрывает вызывающий код после успеха: возвращаться к списку нужно осмысленно,
     * а не оставаться на карточке уже скачанной книги.
     */
    fun onDownload() {
        val target = entry
        val folderId = _uiState.value.selectedFolderId
        if (target == null || !_uiState.value.canStartDownload) {
            _uiState.update { it.copy(error = AppText(R.string.catalogs_entry_error_no_link)) }
            return
        }
        if (folderId == null) {
            _uiState.update { it.copy(error = AppText(R.string.catalogs_entry_error_no_folder)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            opdsRepository.enqueueDownload(
                entry = target,
                catalogId = catalogId,
                targetSourceId = folderId,
            ).fold(
                // Экран остаётся на месте: состояние загрузки приходит подпиской и появляется
                // прямо здесь. Раньше карточка закрывалась, и признак старта увидеть было негде.
                onSuccess = { },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = AppText(
                            R.string.catalogs_entry_error_enqueue,
                            it.text(),
                        ))
                    }
                },
            )
        }
    }
}
