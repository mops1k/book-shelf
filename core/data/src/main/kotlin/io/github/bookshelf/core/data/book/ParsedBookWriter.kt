package io.github.bookshelf.core.data.book

import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.tag.BookTagWriter
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.entity.BookEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись результата разбора метаданных: строка книги, жанры и связи авторов.
 *
 * Три записи живут вместе, потому что расходятся они незаметно: книга показывает автора из
 * `books.authorsText`, а фасет, фильтр и серии автора читают связи — и до этой задачи связи не
 * писал никто, поэтому полка знала автора, а фильтр по нему не находил ни одной книги.
 *
 * Разбор метаданных — основной путь появления авторов после скана (скан создаёт книгу с пустой
 * строкой авторов), поэтому связи пишутся здесь. Скан и импорт OPDS пишут книгу своими
 * компонентами и вызывают [BookAuthorWriter] напрямую.
 */
@Singleton
class ParsedBookWriter @Inject constructor(
    private val bookDao: BookDao,
    private val tagWriter: BookTagWriter,
    private val authorWriter: BookAuthorWriter,
) {

    /**
     * Сохранить разобранную книгу и её связи.
     *
     * @param updated строка книги с уже применёнными метаданными (её пишет воркер разбора);
     * @param tags жанры/теги из метаданных; пустой список снимает прежние.
     */
    suspend fun store(updated: BookEntity, tags: List<String>) {
        bookDao.update(updated)
        tagWriter.replace(updated.id, tags)
        // Авторы берутся из итоговой строки: разбор мог не найти авторов, и тогда сохраняется
        // прежнее значение (у OPDS-книги — из фида), но связи всё равно обязаны ему соответствовать.
        authorWriter.replace(updated.id, updated.authorsText)
    }
}
