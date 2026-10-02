package io.github.bookshelf.core.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.TagEntity
import io.github.bookshelf.core.database.model.ReadingStatus

/**
 * Модели для запросов с JOIN и агрегатами.
 *
 * Здесь лежат и объекты с `@Relation` (карточка книги), и плоские проекции фасетов
 * («Архитектура» §3.5): фасеты считаются запросом к БД, а не в памяти.
 */

/**
 * Книга вместе со связанными сущностями — для экрана карточки книги.
 *
 * `@Relation` с `Junction` разрешается отдельными запросами Room внутри `@Transaction`,
 * поэтому метод DAO, возвращающий этот тип, обязан быть помечен `@Transaction`.
 */
data class BookWithRelations(
    @Embedded val book: BookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookAuthorCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "authorId",
        ),
    )
    val authors: List<AuthorEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CollectionBookCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "collectionId",
        ),
    )
    val collections: List<CollectionEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookTagCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity> = emptyList(),
)

/**
 * Плоская проекция JOIN'а `books × book_authors × authors`.
 *
 * Нужна экрану автора и отладочным запросам: сразу отдаёт и книгу, и порядок автора.
 */
data class BookAuthorRelation(
    val bookId: String,
    val authorId: String,
    val authorName: String,
    val position: Int,
)

/**
 * Фасет «автор»: имя и число книг, доступных при текущих фильтрах.
 *
 * Считается по связям `book_authors`/`authors`: автор, записанный вторым в строке
 * `books.authorsText`, обязан иметь свою строку в фильтре (дефект Александра, 29.09.2026).
 * Строка авторов остаётся только для показа и сортировки.
 */
data class AuthorFacet(
    val name: String,
    val bookCount: Int,
)

/**
 * Фасет «набор авторов»: категория обзора авторов.
 *
 * `setKey` — имена авторов книги, отсортированные по алфавиту и соединённые `|`, поэтому
 * «A, B» и «B, A» дают один ключ; книга с тремя авторами — другой. Подпись строится из ключа
 * (`authorSetLabel`), а сам фильтр по категории идёт не по ключу, а по именам набора: так
 * порядок сортировки в SQL не влияет на то, какие книги попадут в категорию.
 */
data class AuthorSetFacet(
    val setKey: String,
    val bookCount: Int,
)

/** Фасет «серия»: имя серии и число книг. */
data class SeriesFacet(
    val seriesName: String,
    val bookCount: Int,
)

/**
 * Серия конкретного автора: имя серии и число книг.
 *
 * `seriesName = null` означает книги этого автора **вне серий** — в обзоре они показываются
 * отдельной категорией («Вне серий»), иначе такие книги были бы недостижимы из обзора.
 */
data class AuthorSeriesFacet(
    val seriesName: String?,
    val bookCount: Int,
)

/**
 * Фасет «жанр/тег»: имя и число книг.
 *
 * Жанры приходят из метаданных FB2 (`<genre>`) и EPUB (`dc:subject`) и хранятся в `tags`.
 * До появления этого фасета `FilterRepositoryImpl` отдавал `tags = emptyList()`, и жанры
 * в фильтрах и умных полках не были видны вообще.
 */
data class TagFacet(
    val name: String,
    val bookCount: Int,
)

/**
 * Фасет «источник»: папка, из которой пришли книги, и их число.
 *
 * Имя папки может быть пустым: приложение больше не пишет «Папка» в базу (слова выбирает экран).
 */
data class SourceFacet(
    val sourceId: String,
    val sourceName: String,
    val bookCount: Int,
)

/** Фасет «формат»: расширение файла и число книг. */
data class FormatFacet(
    val extension: String,
    val bookCount: Int,
)

/** Фасет «статус чтения»: значение статуса и число книг. */
data class ReadingStatusFacet(
    val readingStatus: ReadingStatus,
    val bookCount: Int,
)
