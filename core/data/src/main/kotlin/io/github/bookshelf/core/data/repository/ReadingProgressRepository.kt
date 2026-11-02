package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.data.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Прогресс чтения («Архитектура» §7, ADR-005, ADR-016).
 *
 * Прогресс приходит из API читалки (уровень L2: позиция текущей открытой книги) либо из
 * собственных источников читалок (KOReader, CoolReader, PocketBook, Librera, XReader).
 * Сервиса библиотеки FBReader (L3) нет: прогресса по каждой книге он не даёт (ADR-016).
 * При отсутствии FBReader/API функция просто не заполняется — это штатное состояние, а не ошибка.
 */
interface ReadingProgressRepository {

    fun observe(bookId: String): Flow<ReadingProgress?>
}
