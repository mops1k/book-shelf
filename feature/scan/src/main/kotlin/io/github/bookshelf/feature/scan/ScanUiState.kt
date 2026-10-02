package io.github.bookshelf.feature.scan


import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.data.model.ScanStatus

/** Какой из трёх экранов модуля показан. */
enum class ScanMode {
    /** Прогресс сканирования: пауза во время прогона и продолжение с сохранённого места. */
    PROGRESS,

    /** Отчёт по завершённому сканированию и список проблемных файлов. */
    REPORT,
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
    /**
     * Причина отказа разбора словами по коду из базы (`books.parseFailure`) или `null`, если кода
     * нет (старая запись): тогда экран подставляет общее переводимое «причина не записана».
     */
    val parseFailure: AppText?,
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
    val durationLabel: AppText? = null,
    /** Проблемные файлы с причинами. */
    val problemFiles: List<ProblemFileUi> = emptyList(),
)

/**
 * Состояние экрана сканирования.
 *
 * Проценты и «можно продолжить» считаются чистыми функциями из [ScanProgressMath.kt].
 *
 * Разрешения дубликатов здесь больше нет: список групп никто не заполнял, а кнопки отвечали
 * «пока не поддерживается». Рабочий поиск дублей живёт в настройках — «Библиотека и сканирование»
 * → «Проверить на дубли»; держать второй, мёртвый вход к той же функции незачем
 * («E-ink UX» §6.7).
 */
data class ScanUiState(
    /** Какой экран модуля показан. */
    val mode: ScanMode = ScanMode.PROGRESS,
    /** Текущий прогресс; `null` — скан не запущен. */
    val progress: ScanProgressUi? = null,
    /** Отчёт последнего завершённого скана; `null` — отчёта нет. */
    val report: ScanReportUi? = null,
    /** Состояние прогона: идёт, пауза, завершён. Экран различает их по нему, а не по флагу. */
    val status: ScanStatus = ScanStatus.IDLE,
    /** Текст ошибки для `EinkErrorBanner`. */
    val error: AppText? = null,
) {
    /** Скан выполняется прямо сейчас. */
    val isRunning: Boolean
        get() = status == ScanStatus.RUNNING

    /**
     * Кнопки, которые показывает экран: пауза во время прогона, продолжение на паузе.
     *
     * Считается чистой функцией [scanActionsOf]: иначе кнопка и состояние разошлись бы.
     */
    val actions: ScanActionsUi
        get() = scanActionsOf(status)

    /**
     * Скан можно продолжить с сохранённого курсора `scan_runs`.
     *
     * Считается чистой функцией [canResumeScan]: общее число файлов заранее неизвестно, и прежнее
     * правило по `filesTotal` держало кнопку отключённой всегда (проверено на устройстве 28.09.2026).
     */
    val canResume: Boolean
        get() = canResumeScan(status = status, filesSeen = progress?.filesSeen ?: 0)
}
