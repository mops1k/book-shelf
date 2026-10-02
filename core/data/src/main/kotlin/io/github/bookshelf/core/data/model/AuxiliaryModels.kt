package io.github.bookshelf.core.data.model

import io.github.bookshelf.core.common.error.DownloadFailure
/**
 * Вспомогательные доменные модели, которыми оперируют репозитории `:core:data`.
 *
 * Вынесены в отдельный файл, чтобы контракты репозиториев читались как «вход/выход», а не как
 * «ещё один десяток файлов по одной data class». Все типы — чистые данные без зависимости от Room,
 * SAF и читалок: feature-модули видят только их.
 */

/** Тип источника книг («Архитектура» §3.3). */
enum class SourceKind { SAF_TREE, DIRECT_PATH, OPDS }

/**
 * Источник книг: выбранная пользователем папка (SAF) или прямой путь в `full`-сборке.
 *
 * @param accessLost persistable-разрешение потеряно (папку переместили/удалили). Книги при этом не
 *   удаляются, а помечаются [Book.isMissing] («Архитектура» §4.1, ADR-010).
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

/** Состояние прогона сканирования («Архитектура» §3.3, §4.5). */
enum class ScanStatus { IDLE, RUNNING, PAUSED, CANCELLED, FINISHED, FAILED }

/**
 * Прогресс сканирования источника.
 *
 * Обновляется не чаще раза в секунду: на e-ink частые перерисовки вредны («E-ink UX» §5).
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

/** Отчёт о завершённом прогоне сканирования (экран «Сканирование и отчёт», «E-ink UX» §6.6). */
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
    /**
     * Убрать номер серии, оставив её название.
     *
     * Отдельный флаг, потому что `null` у [seriesIndex] означает «пользователь это поле не трогал»:
     * без флага «очистить номер» было бы неотличимо от «ничего не менять».
     */
    val clearSeriesIndex: Boolean = false,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val pageCount: Int? = null,
)

/**
 * Ручное переопределение метаданных («Архитектура» §3.1, Sprint 7).
 *
 * Хранится отдельно от `books`, чтобы пересканирование файла не затирало правки пользователя.
 */
data class MetadataOverride(
    val bookId: String,
    val patch: MetadataPatch,
    val origin: MetadataOrigin = MetadataOrigin.MANUAL,
    val updatedAt: Long = 0L,
)

/** Тип полки: ручная или умная (правила в JSON, «Архитектура» §3.3). */
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
    /**
     * Пути обложек первых книг полки — для строки списка полок.
     *
     * У умной полки порядок тот же, что у её состава: берётся предикат правил, а не связи
     * `collection_books`, которых у умной полки нет вовсе.
     */
    val previewCovers: List<String> = emptyList(),
)

/** Каталог OPDS («Архитектура» §3.3, ADR-006). Пароль здесь не хранится — только алиас. */
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

/**
 * Страница фида OPDS: записи, ссылка на следующую страницу и шаблон поиска.
 *
 * `rel="next"` парсился всегда, но до этого не доходил до UI: без него текучая догрузка
 * («листаешь до конца — подгружается дальше») невозможна, и вторая страница фида была
 * недостижима вовсе.
 *
 * @param entries записи страницы: книги или разделы;
 * @param nextUrl абсолютный адрес следующей страницы; `null` — страниц больше нет;
 * @param searchUrl то, что каталог объявил ссылкой `rel="search"`: шаблон OpenSearch с
 *   `{searchTerms}` либо дескриптор, внутри которого шаблон лежит; `null` — каталог поиск не
 *   объявляет, и тогда искать можно только по уже загруженным записям;
 * @param url адрес, с которого пришла страница. Нужен карточке записи: она перечитывает фид по
 *   этому адресу и ищет в нём запись — для результатов поиска это адрес запроса, а не раздела.
 */
data class OpdsPage(
    val entries: List<OpdsEntry> = emptyList(),
    val nextUrl: String? = null,
    val searchUrl: String? = null,
    val url: String? = null,
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
    /**
     * Раздел каталога, а не книга.
     *
     * Признак терялся при маппинге из `:core:opds` в слой данных: оставалась только ссылка на
     * скачивание, и у раздела она была пустой, а отличить его от книги было нечем. Из-за
     * этого под каждым пунктом каталога рисовалась кнопка «Скачать», а нажатие на раздел
     * пыталось поставить в очередь загрузку, которой у него нет.
     */
    val isSection: Boolean = false,
    /** Адрес вложенного фида; задан только у разделов. */
    val sectionHref: String? = null,
    /**
     * Аннотация книги из фида (`summary`/`content`) без разметки.
     *
     * Парсер её читал с самого начала, но при маппинге в домен она терялась — и карточка книги
     * честно показывала «В каталоге нет описания» даже там, где описание было.
     */
    val summary: String? = null,
    /** `dc:language` — «ru», «en». */
    val language: String? = null,
    /** `dc:issued` — год издания. */
    val issued: String? = null,
    /** Дата обновления записи в ISO-8601 (`atom:updated`). */
    val updated: String? = null,
    /** Серия из фида; `null` — каталог её не сообщает. */
    val seriesName: String? = null,
    /**
     * Фид серии: по нему карточка открывает список книг этой серии.
     *
     * OPDS 1.2 не даёт отдельного `rel` для серии — каталоги кладут ссылку в `related`, поэтому
     * признак берётся из адреса (см. `OPDS_SERIES_FEED_MARKER`).
     */
    val seriesUrl: String? = null,
    /** Фид автора: открывает его книги и серии вместо поиска автора вручную. */
    val authorUrl: String? = null,
    /** Форматы, доступные к скачиванию: `FB2`, `EPUB`, `MOBI`. */
    val formats: List<String> = emptyList(),
    /** Жанры из `category/@term`: у Флибусты это «Космическая фантастика», а не код. */
    val genres: List<String> = emptyList(),
    /** `dc:identifier` (ISBN и т. п.). */
    val identifier: String? = null,
)

/** Состояние загрузки из OPDS. `.part` + атомарное переименование — деталь реализации воркера. */
enum class DownloadState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

/** Загрузка из OPDS («OPDS»). */
data class OpdsDownload(
    val id: String,
    val catalogId: String,
    val entryId: String,
    val title: String,
    val downloadUrl: String,
    /** Серия книги из фида: нужна шаблону пути, чтобы разложить загрузки по папкам серий. */
    val seriesName: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val targetSourceId: String? = null,
    val targetRelativeDir: String? = null,
    val bytesDownloaded: Long = 0L,
    val expectedSize: Long? = null,
    /** Код причины отказа загрузки; `null`, если загрузка не падала или ещё идёт. */
    val failure: DownloadFailure? = null,
    /** Диагностика отказа: ответ сервера, отказ провайдера. В интерфейс не выводится. */
    val error: String? = null,
    val bookId: String? = null,
    val createdAt: Long = 0L,
)

/** Источник прогресса чтения: API читалки или ручная отметка. */
enum class ProgressSource { READER_API, MANUAL }

/** Прогресс чтения книги («Архитектура» §3.3). */
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
    /**
     * Форматы (расширения), которые читалка умеет открывать.
     *
     * Нужны экрану «Читалки»: он показывает, чем открывается каждый формат, и предлагает только
     * те читалки, которые этот формат объявили (просьба Александра, 01.10.2026).
     */
    val supportedExtensions: Set<String> = emptySet(),
)
