package io.github.bookshelf.feature.catalogs

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.OpdsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel модуля OPDS (docs/opds.md §3, Sprint 8).
 *
 * На этапе каркаса отдаёт только начальное состояние: сеть не используется вообще,
 * `OpdsRepository` внедрён как контракт и не вызывается. Переключение экранов, ввод в форме
 * и поисковая строка — чистое UI-состояние, они работают уже сейчас.
 *
 * После Sprint 8 появятся действия:
 * * [onLoadFeed] — `OpdsRepository.feed(url)` с conditional GET и кэшем;
 * * [onCheckAndAdd] — реальный запрос корневого фида и добавление каталога;
 * * [onDownloadEntry] — постановка загрузки в `DownloadWorker`;
 * * [onSearchInFeed] — OpenSearch-запрос по шаблону `{searchTerms}`;
 * * [onCancelDownload] — мягкая отмена загрузки.
 */
@HiltViewModel
class CatalogsViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogsUiState())

    /** Состояние модуля OPDS для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<CatalogsUiState> = _uiState.asStateFlow()

    /** Открыть каталог: переключает экран на браузер фида. */
    fun onOpenCatalog(catalogId: String) {
        _uiState.value = _uiState.value.copy(mode = CatalogsMode.FEED, currentFeed = null)
        // STUB(Sprint 8): загрузить корневой фид каталога и построить хлебные крошки
        //  через onLoadFeed(catalogId)
    }

    /** Показать форму ручного добавления каталога. */
    fun onOpenAddForm() {
        _uiState.value = _uiState.value.copy(mode = CatalogsMode.ADD_FORM)
    }

    /** Вернуться к списку каталогов. */
    fun onBackToList() {
        _uiState.value = _uiState.value.copy(mode = CatalogsMode.LIST, feedQuery = "")
    }

    /** Показать экран загрузок. */
    fun onOpenDownloads() {
        _uiState.value = _uiState.value.copy(mode = CatalogsMode.DOWNLOADS)
    }

    /** Изменить название в форме добавления. */
    fun onFormTitleChange(title: String) {
        _uiState.value = _uiState.value.copy(addForm = _uiState.value.addForm.copy(title = title))
    }

    /** Изменить URL в форме добавления. */
    fun onFormUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(addForm = _uiState.value.addForm.copy(url = url))
    }

    /** Изменить логин в форме добавления. */
    fun onFormUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(addForm = _uiState.value.addForm.copy(username = username))
    }

    /** Изменить пароль в форме добавления. */
    fun onFormPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(addForm = _uiState.value.addForm.copy(password = password))
    }

    /** Переключить «открывать каталог сразу». */
    fun onFormOpenImmediatelyChange(openImmediately: Boolean) {
        _uiState.value = _uiState.value.copy(
            addForm = _uiState.value.addForm.copy(openImmediately = openImmediately),
        )
    }

    /** Изменить поисковый запрос внутри фида. */
    fun onFeedQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(feedQuery = query)
    }

    /** Проверить каталог реальным запросом и добавить его. */
    fun onCheckAndAdd() {
        // STUB(Sprint 8): запрос корневого фида, проверка схемы URL, сохранение каталога
        TODO("STUB(Sprint 8): opdsRepository.checkAndAdd(addForm)")
    }

    /** Загрузить фид по URL (корневой или следующая страница). */
    fun onLoadFeed(catalogId: String) {
        // STUB(Sprint 8): загрузить фид с conditional GET (ETag/Last-Modified)
        TODO("STUB(Sprint 8): opdsRepository.feed(catalogId = $catalogId)")
    }

    /** Скачать запись фида в папку библиотеки. */
    fun onDownloadEntry(entryId: String) {
        // STUB(Sprint 8): поставить загрузку в очередь с .part и атомарным переименованием
        TODO("STUB(Sprint 8): opdsRepository.enqueueDownload(entryId = $entryId)")
    }

    /** Поиск по каталогу через OpenSearch. */
    fun onSearchInFeed() {
        // STUB(Sprint 8): подставить запрос в шаблон {searchTerms} и загрузить результат
        TODO("STUB(Sprint 8): opdsRepository.search(feedQuery)")
    }

    /** Отменить активную загрузку (уже скачанное сохраняется). */
    fun onCancelDownload(downloadId: String) {
        // STUB(Sprint 8): мягкая отмена загрузки
        TODO("STUB(Sprint 8): opdsRepository.cancelDownload(downloadId = $downloadId)")
    }
}
