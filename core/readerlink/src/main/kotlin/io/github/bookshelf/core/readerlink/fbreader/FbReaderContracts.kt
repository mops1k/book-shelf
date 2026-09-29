package io.github.bookshelf.core.readerlink.fbreader

import io.github.bookshelf.core.common.error.AppResult

/**
 * Доменный источник прогресса чтения по адресу книги.
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
 * @param pageIndex номер абзаца, если источник его отдаёт.
 * @param locatorJson точная позиция строкой, если источник её отдаёт; формат определяет адаптер,
 *   доменный слой её не разбирает.
 */
data class ReadingProgressSnapshot(
    val percent: Float,
    val pageIndex: Int?,
    val locatorJson: String?,
)

/**
 * Связь с API-сервисом FBReader (уровень L2) — «что открыто в читалке прямо сейчас».
 *
 * Контракт написан по результатам спайка на живом устройстве (Bigme HiBreak, FBReader Premium
 * 3.8.31, 29.09.2026), а не по документации: сервис `com.fbreader.action.API` отвечает методом
 * `request(code, args)`, но **геттера прогресса у него нет**. Вместо него:
 * * `GET_BOOK_FILE_PATH` (505) — адрес открытой книги; для наших книг это ровно тот `content://`,
 *   которым мы отдали книгу в читалку, поэтому книга сопоставляется напрямую с `documentUri`;
 * * `GET_BOOK_TITLE` (502) и `GET_BOOK_LANGUAGE` (501) — название и язык открытой книги;
 * * `GET_PAGE_START` (701) — позиция (абзац и элемент), `GET_PARAGRAPHS_NUMBER` (601) — сколько
 *   всего абзацев: вместе они дают процент;
 * * сервиса библиотеки (L3, прогресс по каждой книге) в этом билде нет вовсе, поэтому его в
 *   контракте не объявляем — обещать недоступное нельзя.
 *
 * Сервис отвечает и когда читалка в фоне (человек ушёл на рабочий стол), пока жив её процесс:
 * проверено привязкой с открытой книгой и нажатым Home. Если система выгрузила читалку, данных
 * нет ни у нас, ни у кого-либо ещё — это ограничение платформы, а не наша недоработка.
 */
interface FbReaderApi {

    /** Открыть сессию: привязаться к сервису с таймаутом. `false` — API недоступен. */
    suspend fun connect(): Boolean

    /** Закрыть сессию; повторный вызов безопасен. */
    suspend fun disconnect()

    /** Доступен ли сервис: сессия открыта и binder жив. */
    fun isAvailable(): Boolean

    /** Что открыто в читалке; `null` — книга не открыта или сервис не ответил. */
    suspend fun currentBook(): FbReaderOpenBook?

    /** Позиция в открытой книге; `null` — книга не открыта или сервис не ответил. */
    suspend fun currentPosition(): FbReaderReadingPosition?
}

/**
 * Книга, открытая в читалке.
 *
 * @param path адрес, которым книга была отдана читалке: для наших книг это `Book.documentUri`.
 * @param title название, как его видит читалка; `null`, если не отдала.
 * @param language язык книги; `null`, если не отдала.
 */
data class FbReaderOpenBook(
    val path: String,
    val title: String?,
    val language: String?,
)

/**
 * Позиция чтения в открытой книге.
 *
 * @param paragraphIndex номер абзаца, на котором открыта страница.
 * @param elementIndex номер элемента внутри абзаца (для текста без форматирования — 0).
 * @param paragraphsNumber всего абзацев в книге; `0`, если читалка их ещё не посчитала.
 */
data class FbReaderReadingPosition(
    val paragraphIndex: Int,
    val elementIndex: Int,
    val paragraphsNumber: Int,
)
