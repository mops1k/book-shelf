package io.github.bookshelf.core.data.author

import io.github.bookshelf.core.database.dao.AuthorDao
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись авторов книги: таблицы `authors` и `book_authors`.
 *
 * Зачем отдельный компонент: `books.authorsText` заполнялся всегда (скан, разбор, правка, OPDS),
 * а нормализованные таблицы — никем. Из-за этого авторское измерение держалось на строке:
 * «Александр Тарарев, Юрий Тарарев» и «Юрий Тарарев, Александр Тарарев» были разными авторами,
 * а книга, где автор записан вторым, по нему не находилась (дефект Александра, 29.09.2026).
 *
 * Идентификатор автора детерминированный (`author:` + имя в нижнем регистре) — как у тегов
 * ([io.github.bookshelf.core.data.tag.BookTagWriter]): повторный разбор метаданных не плодит
 * дубли, а смена регистра в источнике не создаёт второго автора. Существующая запись не
 * переписывается, поэтому показ имени стабилен (первое встреченное написание).
 *
 * `bookCount` — денормализованный счётчик для списков авторов. Обновляется точечно и только у
 * затронутых авторов: полный пересчёт по таблице на каждую книгу был бы проходом по десяткам
 * тысяч строк. Если набор авторов не изменился, не пишется ничего — на этом стоит идемпотентность
 * разового прохода по существующей библиотеке.
 */
@Singleton
class BookAuthorWriter @Inject constructor(
    private val authorDao: AuthorDao,
) {

    /**
     * Заменить набор авторов книги.
     *
     * @param authorsText строка авторов из метаданных, правки или фида OPDS; пустая — связей нет.
     */
    suspend fun replace(bookId: String, authorsText: String?) {
        // Повтор одного имени в разном регистре — тот же автор, поэтому дедупликация по id,
        // а не по строке: «Александр Тарарев, александр тарарев» не должен дать две связи.
        val names = parseAuthors(authorsText).distinctBy { name -> idOf(name) }
        val wantedIds = names.map { name -> idOf(name) }

        val existing = authorDao.getBookAuthors(bookId)
        val changed = existing.map { ref -> ref.authorId } != wantedIds

        val known = authorDao.getByIds(wantedIds).map { author -> author.id }.toSet()
        val fresh = names.filter { name -> idOf(name) !in known }
            .map { name -> AuthorEntity(id = idOf(name), name = name, sortName = sortNameOf(name)) }
        if (fresh.isNotEmpty()) {
            authorDao.upsertAll(fresh)
        }

        if (changed) {
            authorDao.deleteBookAuthors(bookId)
            if (wantedIds.isNotEmpty()) {
                authorDao.insertBookAuthors(
                    wantedIds.mapIndexed { position, authorId ->
                        BookAuthorCrossRef(bookId = bookId, authorId = authorId, position = position)
                    },
                )
            }
        }

        // Счётчик нужен только там, где он мог измениться: появилась новая связь или новый автор.
        val touched = (existing.map { ref -> ref.authorId } + wantedIds).distinct()
        if ((changed || fresh.isNotEmpty()) && touched.isNotEmpty()) {
            authorDao.refreshBookCounts(touched)
        }
    }

    private fun idOf(name: String): String = AUTHOR_ID_PREFIX + name.lowercase()

    private companion object {
        const val AUTHOR_ID_PREFIX = "author:"
    }
}

/**
 * Имя автора для сортировки: фамилия вперёд («Тарарев Александр»).
 *
 * Так список авторов читается как библиотечный каталог: люди ищут по фамилии, а `authors.name`
 * хранит имя в порядке «Имя Фамилия» для показа. Односложное имя остаётся как есть.
 */
internal fun sortNameOf(name: String): String {
    val parts = name.split(' ').filter { part -> part.isNotEmpty() }
    if (parts.size < 2) return name
    return parts.last() + " " + parts.dropLast(1).joinToString(" ")
}
