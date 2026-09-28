package io.github.bookshelf.feature.catalogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.fold
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.DownloadState
import io.github.bookshelf.core.data.model.OpdsCatalog
import io.github.bookshelf.core.data.model.OpdsDownload
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.model.OpdsPage
import io.github.bookshelf.core.data.repository.OpdsRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Килобайт и мегабайт — для подписи размера файла в записи фида. */
private const val BYTES_IN_KILOBYTE = 1024L
private const val BYTES_IN_MEGABYTE = 1024.0 * 1024.0

/** Полная шкала прогресса загрузки: [DownloadUi.progressPercent] хранит проценты, 0..100. */
private const val PERCENT_MAX = 100

/** Формат даты последнего открытия каталога в списке. */
private const val DATE_PATTERN = "dd.MM"

/** Русская локаль: в подписях дробная часть отделяется запятой, а не точкой. */
private val russianLocale: Locale = Locale.forLanguageTag("ru")

/**
 * ViewModel модуля OPDS (docs/opds.md §3, Sprint 8).
 *
 * Списки каталогов и загрузок приходят по подпискам [OpdsRepository.observeCatalogs] и
 * [OpdsRepository.observeDownloads] в [viewModelScope], а сеть дёргается только по действиям
 * пользователя: [onCheckAndAdd], [onLoadFeed], [onDownloadEntry], [onCancelDownload].
 * Ошибка любого из них кладётся в [CatalogsUiState.error] понятным текстом — исключения наружу
 * не пробрасываются.
 */
@HiltViewModel
class CatalogsViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogsUiState())

    /** Состояние модуля OPDS для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<CatalogsUiState> = _uiState.asStateFlow()

    /** Каталог, чей фид открыт: без него нельзя ни грузить фид, ни ставить загрузку в очередь. */
    private var currentCatalogId: String? = null

    /** Записи текущего фида: UI отдаёт только id, а [OpdsRepository.enqueueDownload] ждёт запись. */
    private var currentEntries: Map<String, OpdsEntry> = emptyMap()

    /** Адрес фида, из которого взяты записи: нужен карточке записи, чтобы найти её снова. */
    private var currentFeedUrl: String? = null

    /** Ссылка `rel="next"` текущего уровня: null — страниц больше нет. */
    private var currentNextUrl: String? = null

    /**
     * История открытых разделов: «Назад» возвращает на уровень выше, а не в список каталогов.
     *
     * Уровни хранятся целиком, поэтому возврат мгновенный — без сети и без ожидания.
     */
    private val feedStack = FeedBackStack()

    init {
        observeState()
    }

    /**
     * Открыть каталог: переключить экран на браузер и сразу показать корневой фид.
     *
     * Фид грузится здесь же, а не отдельной командой: нажатие на строку каталога — это и есть
     * переход в браузер, поэтому пустой экран после него смотрел бы как поломка.
     */
    fun onOpenCatalog(catalogId: String) {
        currentCatalogId = catalogId
        currentEntries = emptyMap()
        feedStack.clear()
        _uiState.value = _uiState.value.copy(
            mode = CatalogsMode.FEED,
            currentFeed = null,
            feedQuery = "",
            error = null,
        )
        onLoadFeed(catalogId)
    }

    /** Показать форму ручного добавления каталога. */
    fun onOpenAddForm() {
        _uiState.value = _uiState.value.copy(mode = CatalogsMode.ADD_FORM)
    }

    /**
     * Назад в браузере каталога: на уровень выше, а в корне — к списку каталогов.
     *
     * Возврат берёт уже загруженный уровень, поэтому не ждёт сеть: раньше «Назад» уводила сразу
     * в список каталогов, и из вложенной категории приходилось заходить в каталог заново.
     */
    fun onBack() {
        val level = feedStack.pop()
        if (level == null) {
            onBackToList()
            return
        }
        currentEntries = level.entries.associateBy { it.id }
        currentFeedUrl = level.feedUrl
        currentNextUrl = level.nextUrl
        _uiState.value = _uiState.value.copy(currentFeed = level.feed, error = null)
    }

    /** Вернуться к списку каталогов. */
    fun onBackToList() {
        feedStack.clear()
        currentFeedUrl = null
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

    /**
     * Проверить каталог и добавить его.
     *
     * Отдельного метода проверки в контракте нет: [OpdsRepository.addCatalog] сам ходит по URL и
     * возвращает [AppError.Network] или [AppError.Unauthorized], если каталог недоступен.
     * Пустые название и URL проверяем сами, чтобы не тратить запрос на заведомо плохие данные.
     */
    fun onCheckAndAdd() {
        val form = _uiState.value.addForm
        // Название не обязательно: оно выводится из фида, а если того нет — из хоста.
        // Требовать его здесь значило заставлять вводить то, что сервер знает лучше нас.
        if (form.url.isBlank()) {
            showError("Не удалось добавить каталог", AppError.InvalidArgument("addForm", "укажите адрес каталога"))
            return
        }
        viewModelScope.launch {
            setLoading(true)
            val result = opdsRepository.addCatalog(
                title = form.title.trim(),
                url = form.url.trim(),
                username = form.username.trim().ifBlank { null },
                password = form.password.ifBlank { null },
            )
            setLoading(false)
            result.fold(
                onSuccess = { catalog ->
                    // Форму чистим в ветке успеха: при ошибке поля остаются, чтобы не вводить всё заново.
                    _uiState.value = _uiState.value.copy(
                        mode = CatalogsMode.LIST,
                        addForm = AddCatalogFormUi(),
                        error = null,
                    )
                    if (form.openImmediately) onOpenCatalog(catalog.id)
                },
                onFailure = { showError("Не удалось добавить каталог", it) },
            )
        }
    }

    /**
     * Загрузить корневой фид каталога.
     *
     * Разделы и следующие страницы не открываются: в [OpdsEntry] нет ссылки на родительский фид,
     * поэтому навигация по вложенным разделам появится вместе с ней в контракте.
     */
    fun onLoadFeed(catalogId: String) {
        viewModelScope.launch {
            setLoading(true)
            val result = opdsRepository.browsePage(catalogId)
            setLoading(false)
            result.fold(
                onSuccess = { page ->
                    // Корневой фид — начало истории: разделы прошлого каталога в ней недопустимы.
                    feedStack.clear()
                    currentFeedUrl = null
                    showFeed(catalogId, page.entries, nextUrl = page.nextUrl)
                },
                onFailure = { showError("Не удалось загрузить фид", it) },
            )
        }
    }

    /**
     * Поставить загрузку записи фида в очередь.
     *
     * UI отдаёт только [entryId], поэтому запись ищется среди уже загруженных ([currentEntries]);
     * без неё поставить в очередь нечего — это понятное сообщение, а не падение.
     */
    /**
     * Нажатие на запись фида: раздел открывается, книга скачивается.
     *
     * Раньше обе карточки делали одно и то же — нажатие на раздел пыталось поставить в
     * очередь загрузку, которой у раздела нет, и навигации по каталогу не было вовсе.
     */
    fun onOpenEntry(entryId: String, onOpenEntryCard: (String, String, String?) -> Unit) {
        // Пока идёт загрузка, второй тап только отправил бы второй запрос: на e-ink палец
        // успевает нажать дважды, а фид грузится секундами.
        if (_uiState.value.isLoading) return
        val catalogId = currentCatalogId
        val entry = currentEntries[entryId]
        if (catalogId == null || entry == null) {
            showError("Не удалось открыть запись", AppError.NotFound(entryId, "запись не найдена"))
            return
        }
        val sectionHref = entry.sectionHref
        if (!entry.isSection || sectionHref == null) {
            // Книга: показываем карточку с описанием. Скачивание запускается из неё кнопкой —
            // раньше нажатие сразу ставило загрузку в очередь, и описание было негде посмотреть.
            onOpenEntryCard(catalogId, entryId, currentFeedUrl)
            return
        }
        viewModelScope.launch {
            setLoading(true)
            val result = opdsRepository.browsePage(catalogId, sectionHref)
            setLoading(false)
            result.fold(
                onSuccess = { page ->
                    // Уровень запоминается только при успехе: если раздел не открылся, мы остались
                    // на текущем, и «Назад» должна вести выше именно от него.
                    pushCurrentLevel()
                    currentFeedUrl = sectionHref
                    showFeed(catalogId, page.entries, parentTitle = entry.title, nextUrl = page.nextUrl)
                },
                onFailure = { showError("Не удалось открыть раздел", it) },
            )
        }
    }

    /** Запомнить текущий уровень перед переходом в раздел — для возврата по «Назад». */
    private fun pushCurrentLevel() {
        val feed = _uiState.value.currentFeed ?: return
        feedStack.push(
            FeedLevel(
                entries = currentEntries.values.toList(),
                feed = feed,
                feedUrl = currentFeedUrl,
                nextUrl = currentNextUrl,
            ),
        )
    }

    fun onDownloadEntry(entryId: String) {
        val catalogId = currentCatalogId
        if (catalogId == null) {
            showError(
                "Не удалось поставить загрузку в очередь",
                AppError.NotFound(entryId, "сначала откройте каталог"),
            )
            return
        }
        val entry = currentEntries[entryId]
        if (entry == null) {
            showError(
                "Не удалось поставить загрузку в очередь",
                AppError.NotFound(entryId, "запись не найдена в загруженном фиде, обновите фид"),
            )
            return
        }
        viewModelScope.launch {
            setLoading(true)
            // Папка назначения берётся из уже добавленных источников: пользователь завёл папку
            // ради того, чтобы её наполнять, и спрашивать её при каждой загрузке — лишний шаг.
            // Если источников нет, репозиторий вернёт понятную ошибку, а не потеряет загрузку.
            val targetSourceId = sourceRepository.observeSources().first()
                ?.firstOrNull { it.enabled && !it.accessLost }
                ?.id
            val result = opdsRepository.enqueueDownload(
                entry = entry,
                catalogId = catalogId,
                targetSourceId = targetSourceId,
            )
            setLoading(false)
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(error = null) },
                onFailure = { showError("Не удалось поставить загрузку в очередь", it) },
            )
        }
    }

    /**
     * Поиск по каталогу через OpenSearch.
     *
     * В контракте [OpdsRepository] нет ни метода поиска, ни шаблона `{searchTerms}`, а каталог
     * хранит только корневой URL, поэтому подставить запрос нечем. Вместо падения в баннер уходит
     * сообщение «пока не поддерживается».
     */
    fun onSearchInFeed() {
        _uiState.value = _uiState.value.copy(
            error = "Поиск по фиду пока не поддерживается: в OpdsRepository нет метода поиска " +
                "и шаблона OpenSearch",
        )
    }

    /** Отменить активную загрузку (уже скачанное сохраняется). */
    fun onCancelDownload(downloadId: String) {
        viewModelScope.launch {
            val result = opdsRepository.cancelDownload(downloadId)
            result.fold(
                onSuccess = { _uiState.value = _uiState.value.copy(error = null) },
                onFailure = { showError("Не удалось отменить загрузку", it) },
            )
        }
    }

    /**
     * Подписки на каталоги и загрузки.
     *
     * Обе подписки постоянные: добавление, удаление каталога и прогресс загрузки приезжают
     * из репозитория сами, поэтому кнопки «перечитать» у этих списков нет.
     */
    private fun observeState() {
        viewModelScope.launch {
            opdsRepository.observeCatalogs()
                .catch { throwable -> showError("Не удалось загрузить каталоги", throwable.toAppError()) }
                .collect { catalogs ->
                    _uiState.value = _uiState.value.copy(catalogs = catalogs.map { it.toCatalogUi() })
                }
        }
        viewModelScope.launch {
            opdsRepository.observeDownloads()
                .catch { throwable -> showError("Не удалось загрузить загрузки", throwable.toAppError()) }
                .collect { downloads ->
                    _uiState.value = _uiState.value.copy(downloads = downloads.map { it.toDownloadUi() })
                }
        }
    }

    /** Показать загруженный фид и запомнить его записи для [onDownloadEntry]. */
    private fun showFeed(
        catalogId: String,
        entries: List<OpdsEntry>,
        parentTitle: String? = null,
        nextUrl: String? = null,
        breadcrumbs: List<String>? = null,
    ) {
        // В фидах жанров Флибуста одна и та же книга встречается несколько раз: у неё сразу
        // несколько жанров. Повторные записи и сбивали ключ сетки («Key … was already used»),
        // из-за чего приложение падало на третьем вложенном уровне. Показываем книгу один раз.
        val unique = entries.distinctBy { it.id }
        currentEntries = unique.associateBy { it.id }
        currentNextUrl = nextUrl
        val catalog = _uiState.value.catalogs.firstOrNull { it.id == catalogId }
        _uiState.value = _uiState.value.copy(
            currentFeed = FeedUi(
                title = catalog?.title ?: "Каталог",
                // Хлебные крошки копятся при переходе в раздел: без них в глубоком фиде
                // непонятно, где вы находитесь и куда возвращаться. При догрузке страницы
                // крошки приходят готовыми: дописывать текущий уровень второй раз нельзя —
                // получалось «… / Все новинки / Все новинки».
                breadcrumbs = breadcrumbs ?: if (parentTitle == null) {
                    listOfNotNull(catalog?.title)
                } else {
                    _uiState.value.currentFeed?.breadcrumbs.orEmpty() + parentTitle
                },
                entries = unique.map { it.toFeedEntryUi() },
                // Фид целиком из разделов — навигационный; книги в нём означают листинг.
                isNavigation = unique.isNotEmpty() && unique.all { entry -> entry.isSection },
                // Есть ли следующая страница: по этому признаку UI решает, догружать ли дальше.
                hasNextPage = nextUrl != null,
            ),
            error = null,
        )
    }

    /**
     * Догрузить следующую страницу фида (`rel="next"`).
     *
     * Вызывается, когда пользователь долистал список до конца: без этого фид длиннее одной
     * страницы обрывался, и дальше первой страницы уйти было нельзя. Новые записи дописываются
     * к уже показанным (с тем же отсечением повторов), а не заменяют их.
     */
    fun onLoadNextPage() {
        val catalogId = currentCatalogId ?: return
        val nextUrl = currentNextUrl ?: return
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val result = opdsRepository.browsePage(catalogId, nextUrl)
            _uiState.value = _uiState.value.copy(isLoadingMore = false)
            result.fold(
                onSuccess = { page ->
                    appendFeed(catalogId, page)
                },
                onFailure = { showError("Не удалось догрузить страницу каталога", it) },
            )
        }
    }

    /** Дописать страницу к текущему фиду: список остаётся на месте, растёт только его конец. */
    private fun appendFeed(catalogId: String, page: OpdsPage) {
        val feed = _uiState.value.currentFeed ?: return
        val merged = (currentEntries.values.toList() + page.entries).distinctBy { it.id }
        showFeed(
            catalogId = catalogId,
            entries = merged,
            // Крошки уже собраны на предыдущих шагах — переносим их как есть.
            breadcrumbs = feed.breadcrumbs,
            nextUrl = page.nextUrl,
        )
    }

    /** Флаг «идёт сетевой запрос»: UI не должен давать вторую команду поверх первой. */
    private fun setLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
    }

    /**
     * Показать ошибку действия: что не получилось и почему.
     *
     * У части ошибок [AppError.message] пуст, поэтому для них текст берётся по типу — баннер
     * не должен оставаться пустым.
     */
    private fun showError(action: String, error: AppError) {
        val detail = error.message ?: when (error) {
            is AppError.Unsupported -> "пока не поддерживается (${error.what})"
            is AppError.NotFound -> "не найдено: ${error.what}"
            is AppError.InvalidArgument -> "неверное значение: ${error.what}"
            is AppError.PermissionDenied -> "нет доступа"
            is AppError.Io -> "ошибка ввода-вывода"
            is AppError.Parse -> "не удалось разобрать данные: ${error.format}"
            is AppError.Network -> "ошибка сети" + (error.httpCode?.let { " (HTTP $it)" } ?: "")
            is AppError.Unauthorized -> "нужна авторизация"
            AppError.AccessLost -> "доступ к источнику потерян"
            is AppError.Unknown -> "неизвестная ошибка"
        }
        _uiState.value = _uiState.value.copy(error = "$action: $detail")
    }
}

/**
 * Каталог для строки списка.
 *
 * [CatalogUi.isAvailable] отражает `enabled`: отдельного статуса доступности в контракте нет,
 * а сетевые ошибки показываются баннером [CatalogsUiState.error].
 */
private fun OpdsCatalog.toCatalogUi(): CatalogUi = CatalogUi(
    id = id,
    title = title,
    url = url,
    requiresAuth = requiresAuth,
    lastOpenedLabel = lastOpenedAt?.let { openedAt ->
        "Последний раз: " + SimpleDateFormat(DATE_PATTERN, russianLocale).format(Date(openedAt))
    },
    isAvailable = enabled,
)

/**
 * Запись фида для сетки.
 *
 * Формат и размер берутся из MIME и `expectedSize`; локальной обложки ещё нет, поэтому
 * [FeedEntryUi.coverPath] остаётся `null` и обложку рисует заглушка по названию.
 */
private fun OpdsEntry.toFeedEntryUi(): FeedEntryUi = FeedEntryUi(
    id = id,
    isSection = isSection,
    href = sectionHref,
    title = title,
    authors = authorsText.orEmpty(),
    coverUrl = coverUrl,
    formats = mimeType?.let { listOf(it.substringAfter('/').uppercase()) }.orEmpty(),
    sizeLabel = expectedSize?.let { size ->
        val megabytes = size / BYTES_IN_MEGABYTE
        if (megabytes >= 1.0) {
            String.format(russianLocale, "%.1f МБ", megabytes)
        } else {
            "${size / BYTES_IN_KILOBYTE} КБ"
        }
    },
)

/**
 * Загрузка для строки очереди.
 *
 * Прогресс считается из скачанных байт: размер известен не всегда, поэтому при `null` показывается
 * 0, а готовая загрузка — как 100%. На паузе загрузка остаётся активной: её ещё можно отменить.
 */
private fun OpdsDownload.toDownloadUi(): DownloadUi {
    val total = expectedSize
    val percent = when {
        state == DownloadState.DONE -> PERCENT_MAX
        total == null || total <= 0L -> 0
        else -> ((bytesDownloaded.toDouble() / total) * PERCENT_MAX).toInt().coerceIn(0, PERCENT_MAX)
    }
    val isActive = state == DownloadState.QUEUED ||
        state == DownloadState.RUNNING ||
        state == DownloadState.PAUSED
    return DownloadUi(
        id = id,
        title = title,
        stateLabel = when (state) {
            DownloadState.QUEUED -> "В очереди"
            DownloadState.RUNNING -> "Скачивается"
            DownloadState.PAUSED -> "На паузе"
            DownloadState.DONE -> "Готово"
            DownloadState.FAILED -> "Ошибка"
            DownloadState.CANCELLED -> "Отменено"
        },
        progressPercent = percent,
        isActive = isActive,
        bookId = bookId,
        error = error,
    )
}
