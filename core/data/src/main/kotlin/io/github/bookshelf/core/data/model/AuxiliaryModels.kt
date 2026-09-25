package io.github.bookshelf.core.data.model

/**
 * Вспомогательные доменные модели, которыми оперируют репозитории `:core:data`.
 *
 * Вынесены в отдельный файл, чтобы контракты репозиториев читались как «вход/выход», а не как
 * «ещё один десяток файлов по одной data class». Все типы — чистые данные без зависимости от Room,
 * SAF и читалок: feature-модули видят только их.
 */

/** Тип источника книг (docs/architecture.md §3.3). */
enum class SourceKind { SAF_TREE, DIRECT_PATH, OPDS }

/**
 * Источник книг: выбранная пользователем папка (SAF) или прямой путь в `full`-сборке.
 *
 * @param accessLost persistable-разрешение потеряно (папку переместили/удалили). Книги при этом не
 *   удаляются, а помечаются [Book.isMissing] (docs/architecture.md §4.1, ADR-010).
 */
data class Source(
    val id: String,
    val uri: String,
    val displayName: String,
    val kind: SourceKind,
    val enabled: Boolean = true,
    val recursive: Boolean = true,
    val excludeGlobs: List<String> = emptyList(),
    val lastScanAt: Long? = null,
    val documentCount: Int = 0,
    val accessLost: Boolean = false,
)

/** Состояние прогона сканирования (docs/architecture.md §3.3, §4.5). */
enum class ScanStatus { IDLE, RUNNING, PAUSED, CANCELLED, FINISHED, FAILED }

/**
 * Прогресс сканирования источника.
 *
 * Обновляется не чаще раза в секунду: на e-ink частые перерисовки вредны (docs/eink-ux.md §5).
 * `currentPath` — курсор обхода; по нему скан возобновляется после `Service.onTimeout` (ADR-010).
 */
data class ScanProgress(
    val sourceId: String,
    val status: ScanStatus,
    val filesSeen: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val failed: Int = 0,
    val totalKnown: Int? = null,
    val currentPath: String? = null,
    val startedAt: Long = 0L,
    val finishedAt: Long? = null,
)

/** Отчёт о завершённом прогоне сканирования (экран «Сканирование и отчёт», docs/eink-ux.md §6.6). */
data class ScanReport(
    val runId: String,
    val sourceId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: ScanStatus = ScanStatus.FINISHED,
    val filesSeen: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val failedCount: Int = 0,
    val error: String? = null,
)

/**
 * Патч метаданных книги: меняются только непустые поля.
 *
 * `null` означает «не трогать», а не «очистить» — иначе отмена редактирования одного поля
 * стирала бы остальные.
 */
data class MetadataPatch(
    val title: String? = null,
    val subtitle: String? = null,
    val authorsText: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val pageCount: Int? = null,
)

/**
 * Ручное переопределение метаданных (docs/architecture.md §3.1, Sprint 7).
 *
 * Хранится отдельно от `books`, чтобы пересканирование файла не затирало правки пользователя.
 */
data class MetadataOverride(
    val bookId: String,
    val patch: MetadataPatch,
    val origin: MetadataOrigin = MetadataOrigin.MANUAL,
    val updatedAt: Long = 0L,
)

/** Тип полки: ручная или умная (правила в JSON, docs/architecture.md §3.3). */
enum class CollectionType { MANUAL, SMART }

/** Коллекция/полка. Для [CollectionType.SMART] правила лежат в [rulesJson]. */
data class Collection(
    val id: String,
    val name: String,
    val type: CollectionType = CollectionType.MANUAL,
    val iconKey: String? = null,
    val position: Int = 0,
    val bookCount: Int = 0,
    val createdAt: Long = 0L,
    val rulesJson: String? = null,
)

/** Каталог OPDS (docs/architecture.md §3.3, ADR-006). Пароль здесь не хранится — только алиас. */
data class OpdsCatalog(
    val id: String,
    val title: String,
    val url: String,
    val requiresAuth: Boolean = false,
    val username: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val lastOpenedAt: Long? = null,
)

/** Запись фида OPDS, пригодная для списка и для загрузки. */
data class OpdsEntry(
    val id: String,
    val title: String,
    val authorsText: String? = null,
    val coverUrl: String? = null,
    val acquisitionUrl: String? = null,
    val mimeType: String? = null,
    val expectedSize: Long? = null,
)

/** Состояние загрузки из OPDS. `.part` + атомарное переименование — деталь реализации воркера. */
enum class DownloadState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

/** Загрузка из OPDS (docs/opds.md). */
data class OpdsDownload(
    val id: String,
    val catalogId: String,
    val entryId: String,
    val title: String,
    val downloadUrl: String,
    val state: DownloadState = DownloadState.QUEUED,
    val targetSourceId: String? = null,
    val targetRelativeDir: String? = null,
    val bytesDownloaded: Long = 0L,
    val expectedSize: Long? = null,
    val error: String? = null,
    val bookId: String? = null,
    val createdAt: Long = 0L,
)

/** Источник прогресса чтения: API читалки или ручная отметка. */
enum class ProgressSource { READER_API, MANUAL }

/** Прогресс чтения книги (docs/architecture.md §3.3). */
data class ReadingProgress(
    val bookId: String,
    val percent: Float,
    val source: ProgressSource = ProgressSource.MANUAL,
    val profileId: String? = null,
    val locatorJson: String? = null,
    val updatedAt: Long = 0L,
)

/** Краткая карточка читалки для экрана «Читалки» (полное описание — в `:core:readerlink`). */
data class ReaderProfileSummary(
    val id: String,
    val name: String,
    val packageName: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val priority: Int = 0,
)
