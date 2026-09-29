package io.github.bookshelf.core.readerlink.fbreader

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull

/**
 * Адаптер [FbReaderApi] к доменному [ReadingProgressSource].
 *
 * Читалка не отдаёт процент готовым (спайк 29.09.2026): есть позиция **текущей открытой** книги
 * (абзац из кода 701) и общее число абзацев (601). Процент считается из них, а книга сопоставляется
 * по адресу, которым мы её отдали читалке: сервис возвращает ровно наш `content://`, то есть
 * сравнение идёт с `Book.documentUri` и не зависит от догадок о путях и хешах.
 *
 * Класс без `@Inject`: [FbReaderApi] привязывается к реальному клиенту сервиса отдельно.
 */
class FbReaderAdapter(
    private val api: FbReaderApi,
) : ReadingProgressSource {

    /**
     * Прогресс книги: `Failure`, если открыта другая книга или читалка не ответила.
     *
     * Молча вернуть нулевой процент в этих случаях нельзя — на полке это выглядело бы как
     * «книга не начата», хотя человек, возможно, её читает.
     */
    override suspend fun progressFor(filePath: String): AppResult<ReadingProgressSnapshot> {
        val snapshot = appRunCatching { snapshotFor(filePath) }.getOrNull()
        return if (snapshot != null) {
            AppResult.success(snapshot)
        } else {
            AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения",
                    message = "читалка не отдала позицию книги $filePath",
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
    private suspend fun snapshotFor(filePath: String): ReadingProgressSnapshot? {
        val book = api.currentBook() ?: return null
        if (book.path != filePath) return null
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
