package io.github.bookshelf.core.data.author

import androidx.room.withTransaction
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.dao.AuthorDao
import io.github.bookshelf.core.database.dao.BookDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разовый проход по существующей библиотеке: связи авторов для книг, у которых есть строка
 * авторов, но нет связей.
 *
 * Зачем: авторское измерение (фасет, фильтр, серии автора, группировка) перешло на
 * `authors`/`book_authors`, а в собранной ранее библиотеке эти таблицы пусты — обновление без
 * прохода показало бы ноль книг у каждого автора. Свежесобранной библиотеке проход не нужен:
 * авторов пишут разбор метаданных и импорт OPDS.
 *
 * Идемпотентность не на флаге в настройках, а на данных ([AuthorDao.idsWithoutAuthors]): проход
 * берёт только книги без связей, поэтому повторный запуск (в том числе при каждом следующем
 * старте приложения) не пишет ни одной строки, а книга, появившаяся позже любым путём, получит
 * связи сама. Флаг пришлось бы сбрасывать при импорте и восстанавливать после прерванного
 * прохода — выборка по данным этого не требует.
 *
 * Пачки, а не один общий проход: транзакция на всю библиотеку держала бы блокировку записи
 * минутами, а на e-ink это ещё и заметный расход батареи.
 */
@Singleton
class AuthorBackfill @Inject constructor(
    private val database: BookshelfDatabase,
    private val bookDao: BookDao,
    /** Выборка книг без связей авторов — в DAO авторов: там же живут сами связи. */
    private val authorDao: AuthorDao,
    private val authorWriter: BookAuthorWriter,
) {

    /** Заполнить связи авторов книгам, у которых их ещё нет. */
    suspend fun runOnce() {
        val ids = authorDao.idsWithoutAuthors()
        ids.chunked(BATCH_SIZE).forEach { batch ->
            database.withTransaction {
                batch.forEach { bookId ->
                    // Книга могла исчезнуть между выборкой и записью (скан, удаление) — для
                    // прохода это нормально: связей у неё уже нет и не будет.
                    val book = bookDao.getById(bookId) ?: return@forEach
                    authorWriter.replace(book.id, book.authorsText)
                }
            }
        }
    }

    private companion object {
        /** Размер пачки: одна транзакция на 200 книг — короткая блокировка записи. */
        const val BATCH_SIZE = 200
    }
}
