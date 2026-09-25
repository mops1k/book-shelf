package io.github.bookshelf.feature.scan

/** Какой из трёх экранов модуля показан. */
enum class ScanMode {
    /** Прогресс сканирования с возможностью мягкой отмены и продолжения. */
    PROGRESS,

    /** Отчёт по завершённому сканированию и список проблемных файлов. */
    REPORT,

    /** Группы дубликатов по `contentHash`. */
    DUPLICATES,
}

/**
 * Прогресс сканирования (UI-модель).
 *
 * Соответствует `ScanProgress` из `core:data.model`, но содержит только то, что показывает
 * экран. Заполняется из `ScanRepository.observeProgress(sourceId)` в Sprint 2.
 */
data class ScanProgressUi(
    /** Название источника, который сканируется. */
    val sourceName: String = "",
    /** Сколько файлов уже просмотрено. */
    val filesSeen: Int = 0,
    /** Сколько всего файлов ожидается; `null` — оценка ещё не известна. */
    val filesTotal: Int? = null,
    /** Добавлено новых записей. */
    val added: Int = 0,
    /** Обновлено изменившихся записей. */
    val updated: Int = 0,
    /** Помечено пропавшими. */
    val removed: Int = 0,
    /** Файлов, которые не удалось разобрать. */
    val failed: Int = 0,
)

/** Файл, который не удалось разобрать, вместе с причиной. */
data class ProblemFileUi(
    /** `books.id`. */
    val id: String,
    /** Имя файла или путь внутри источника. */
    val fileName: String,
    /** Причина: «не удалось разобрать: повреждён ZIP». */
    val reason: String,
)

/** Отчёт по завершённому сканированию (UI-модель). */
data class ScanReportUi(
    /** Добавлено записей. */
    val added: Int = 0,
    /** Обновлено записей. */
    val updated: Int = 0,
    /** Помечено пропавшими. */
    val removed: Int = 0,
    /** Не разобрано. */
    val failed: Int = 0,
    /** Длительность скана подписью: «1 мин 20 с». */
    val durationLabel: String? = null,
    /** Проблемные файлы с причинами. */
    val problemFiles: List<ProblemFileUi> = emptyList(),
)

/** Одна копия книги внутри группы дубликатов. */
data class DuplicateCopyUi(
    /** `books.id`. */
    val bookId: String,
    /** Путь внутри источника. */
    val path: String,
    /** Размер файла подписью. */
    val sizeLabel: String,
    /** true — эту копию пользователь решил оставить. */
    val isKeeper: Boolean = false,
)

/** Группа книг с одинаковым `contentHash` (docs/eink-ux.md §6.7). */
data class DuplicateGroupUi(
    /** Идентификатор группы. */
    val id: String,
    /** Название книги, общее для группы. */
    val title: String,
    /** Копии: файлы никогда не удаляются, только записи. */
    val copies: List<DuplicateCopyUi> = emptyList(),
)

/**
 * Состояние экрана сканирования.
 *
 * На этапе каркаса заполнено значением по умолчанию: работа с `ScanRepository` появится
 * в Sprint 2, разрешение дубликатов — в Sprint 7. Проценты и «можно продолжить» считаются
 * чистыми функциями из [ScanProgressMath.kt].
 */
data class ScanUiState(
    /** Какой экран модуля показан. */
    val mode: ScanMode = ScanMode.PROGRESS,
    /** Текущий прогресс; `null` — скан не запущен. */
    val progress: ScanProgressUi? = null,
    /** Отчёт последнего завершённого скана; `null` — отчёта нет. */
    val report: ScanReportUi? = null,
    /** Группы дубликатов. */
    val duplicates: List<DuplicateGroupUi> = emptyList(),
    /** Скан выполняется прямо сейчас. */
    val isRunning: Boolean = false,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: String? = null,
) {
    /**
     * Скан можно продолжить с сохранённого курсора `scan_runs`.
     *
     * Считается чистой функцией [canResumeScan], а не хранится отдельным полем: иначе
     * состояние и подпись кнопки могли бы разойтись.
     */
    val canResume: Boolean
        get() = canResumeScan(
            isRunning = isRunning,
            filesSeen = progress?.filesSeen ?: 0,
            filesTotal = progress?.filesTotal,
        )
}
