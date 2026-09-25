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
 * (docs/architecture.md §3.5): фасеты считаются запросом к БД, а не в памяти.
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

/** Фасет «автор»: автор и число книг, доступных при текущих фильтрах. */
data class AuthorFacet(
    val authorId: String,
    val name: String,
    val sortName: String,
    val bookCount: Int,
)

/** Фасет «серия»: имя серии и число книг. */
data class SeriesFacet(
    val seriesName: String,
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
