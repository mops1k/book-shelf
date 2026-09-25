package io.github.bookshelf.core.readerlink.fbreader

import io.github.bookshelf.core.common.error.AppResult

/**
 * Доменный источник прогресса чтения по пути файла.
 *
 * Живёт в модуле читалок, потому что других источников прогресса в v1.0 нет; при появлении
 * встроенной читалки (Phase 4) интерфейс станет общей точкой для нескольких реализаций.
 */
interface ReadingProgressSource {

    /** Прогресс книги по её реальному пути; `AppResult.Failure`, если книга не найдена. */
    suspend fun progressFor(filePath: String): AppResult<ReadingProgressSnapshot>
}

/**
 * Снимок прогресса чтения.
 *
 * @param percent прогресс в процентах, 0f..100f.
 * @param pageIndex номер абзаца/страницы, если источник его отдаёт.
 * @param locatorJson точная позиция в виде JSON (`TextPosition`), если источник её отдаёт;
 *   формат строки определяет адаптер, доменный слой её не разбирает.
 */
data class ReadingProgressSnapshot(
    val percent: Float,
    val pageIndex: Int?,
    val locatorJson: String?,
)

/**
 * Адаптер [FbReaderApi] к доменному [ReadingProgressSource].
 *
 * Класс без `@Inject`: конструктор принимает [FbReaderApi], привязка которого к AIDL-реализации
 * появляется в Sprint 7 вместе с вендорингом AIDL. До этого момента DI-граф его не собирает.
 */
class FbReaderAdapter(
    api: FbReaderApi,
) : ReadingProgressSource {

    // STUB(Sprint 7): сравнить getBookFilePath() с filePath, затем прочитать
    // getBookProgress()/getBookHash() и собрать ReadingProgressSnapshot.

    override suspend fun progressFor(filePath: String): AppResult<ReadingProgressSnapshot> = TODO(
        "STUB(Sprint 7): прочитать прогресс текущей книги и сопоставить её с filePath",
    )
}
