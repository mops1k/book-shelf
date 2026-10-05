package io.github.bookshelf.core.readerlink.fbreader

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник прогресса FBReader: API-сервис читалки как источник уровня L2.
 *
 * Читалка не отдаёт процент готовым (спайк 29.09.2026): есть позиция **текущей открытой** книги
 * (абзац из кода 701) и общее число абзацев (601). Процент считается из них, а книга сопоставляется
 * по адресу, которым мы её отдали читалке: сервис возвращает ровно наш `content://`, то есть
 * сравнение идёт с `Book.documentUri` и не зависит от догадок о путях и хешах.
 *
 * Привязку к сервису держит сам источник ([connect]/[disconnect]): раньше это делал сценарий
 * синхронизации, и с появлением второго источника «забыть закрыть чужой сервис» стало вопросом
 * времени — теперь закрытие привязано к тому, кто открыл.
 */
@Singleton
class FbReaderAdapter @Inject constructor(
    private val api: FbReaderApi,
) : ReadingProgressSource {

    override val id: ProgressSourceId = ProgressSourceId.FBREADER_AIDL

    /** FBReader сам отдаёт адрес открытой книги (`GET_BOOK_FILE_PATH`, 505). */
    override val knowsOpenBook: Boolean = true

    override suspend fun connect(): Boolean = api.connect()

    override suspend fun disconnect() = api.disconnect()

    /**
     * Книга, открытая в читалке.
     *
     * Название не отдаём: у FBReader это `GET_BOOK_TITLE`, то есть данные книги, а не адрес; для
     * журнала достаточно адреса, а полноту ответа добавим, когда она понадобится диагностике.
     */
    override suspend fun openedBook(): ReaderBookAddress? =
        appRunCatching { api.currentBook() }.getOrNull()
            ?.let { ReaderBookAddress(documentUri = it.path) }

    /**
     * Прогресс книги: `Failure`, если открыта другая книга или читалка не ответила.
     *
     * Молча вернуть нулевой процент в этих случаях нельзя — на полке это выглядело бы как
     * «книга не начата», хотя человек, возможно, её читает.
     */
    override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
        // Читалка узнаёт книгу по своему адресу: если она читает копию, спросить надо про копию.
        val snapshot = appRunCatching { snapshotFor(book.readerAddress) }.getOrNull()
        return if (snapshot != null) {
            AppResult.success(snapshot)
        } else {
            AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения",
                    message = "читалка не отдала позицию книги ${book.readerAddress}",
                ),
            )
        }
    }

    /**
     * Снимок прогресса; `null` — читалка не знает именно эту книгу или ещё не посчитала текст.
     *
     * Отдельным методом, чтобы «нет данных» и «сервис упал» различались: исключения ловит
     * вызывающий, а `null` означает «показывать нечего».
     */
    private suspend fun snapshotFor(documentUri: String): ReadingProgressSnapshot? {
        val book = api.currentBook() ?: return null
        if (book.path != documentUri) return null
        val position = api.currentPosition() ?: return null
        // Ноль абзацев означает «читалка ещё не посчитала»: показать 0 % значило бы соврать.
        if (position.paragraphsNumber <= 0) return null
        return ReadingProgressSnapshot(
            percent = (
                position.paragraphIndex.toFloat() * PERCENT / position.paragraphsNumber.toFloat()
                ).coerceIn(0f, PERCENT),
            pageIndex = position.paragraphIndex,
            locatorJson = null,
        )
    }

    private companion object {
        const val PERCENT = 100f
    }
}
