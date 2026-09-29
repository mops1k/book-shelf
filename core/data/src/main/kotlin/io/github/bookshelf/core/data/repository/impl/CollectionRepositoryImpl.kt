package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.mapper.BookPageSql
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.repository.CollectionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.UUID
import io.github.bookshelf.core.database.model.CollectionType as DatabaseCollectionType
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.dao.CollectionBookCount
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.data.mapper.toDomain
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.common.error.appRunCatching

/**
 * Заглушка [CollectionRepository].
 *
 * Что должна делать (Sprint 7, ADR-008):
 * * CRUD `collections` (тип `MANUAL`/`SMART`, порядок, иконка);
 * * `SMART`-полки вычисляются тем же предикатом, что и фильтры, по `rulesJson` — одна реализация
 *   на фильтр и на полку, иначе поведение разъедется;
 * * `collection_books` с позицией для ручных полок.
 */
@Singleton
class CollectionRepositoryImpl @Inject constructor(
    private val dao: CollectionDao,
    private val bookDao: BookDao,
    private val clock: AppClock,
) : CollectionRepository {

    /**
     * Полки вместе с числом книг и обложками первых книг.
     *
     * Число берётся отдельным запросом: в `collections` такой колонки нет, доменное поле
     * `bookCount` было всегда нулём, и полка с книгами показывалась как пустая.
     *
     * У умной полки книг в связях нет вовсе — её состав считается по правилам, поэтому и счётчик,
     * и обложки для неё берутся тем же предикатом. Без этого умная полка показывала бы «0 книг»,
     * открываясь при этом непустой.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCollections(): Flow<List<Collection>> =
        dao.observeAll().flatMapLatest { rows ->
            if (rows.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(rows.map { row -> detailsOf(row) }) { items -> items.toList() }
            }
        }

    /**
     * Полка для строки списка: число книг и обложки первых книг.
     *
     * Обложки нужны самой строке: без них полка в списке не узнаётся — там было только название
     * и число. У умной полки обложки берутся предикатом правил, у ручной — связями по позиции.
     */
    private fun detailsOf(row: CollectionEntity): Flow<Collection> {
        val rules = smartRulesOf(row)
        if (rules != null) {
            return combine(
                bookDao.observeCount(BookPageSql.count(smartQuery(rules))),
                bookDao.observePage(BookPageSql.select(smartQuery(rules, PREVIEW_COVERS)))
                    .map { books -> books.mapNotNull { book -> book.coverPath } },
            ) { count: Int, covers: List<String> ->
                row.toDomain().copy(bookCount = count, previewCovers = covers)
            }
        }
        return combine(
            dao.observeBookCounts(),
            dao.observePreviewCovers(row.id, PREVIEW_COVERS),
        ) { counts: List<CollectionBookCount>, covers: List<String> ->
            row.toDomain().copy(
                bookCount = counts.firstOrNull { count -> count.collectionId == row.id }?.bookCount ?: 0,
                previewCovers = covers,
            )
        }
    }

    /**
     * Книги полки.
     *
     * Тип `combine` задан явно — `Array<Book?>`: без него `T` не выводится, и вызов не
     * собирается. Состав книг меняется независимо от полки, поэтому смена набора id
     * переподписывает поток, а не фильтрует старый список.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeBooks(collectionId: String): Flow<List<Book>> =
        dao.observeAll()
            .map { rows -> rows.firstOrNull { row -> row.id == collectionId } }
            .distinctUntilChanged()
            .flatMapLatest { entity -> booksOf(collectionId, entity) }

    /**
     * Состав полки: у умной — по правилам, у ручной — по `collection_books`.
     *
     * Умная полка читается тем же запросом, что и полка после фильтра (`BookPageSql`), поэтому
     * «полка по жанру» показывает ровно то же, что фильтр по этому жанру.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun booksOf(collectionId: String, entity: CollectionEntity?): Flow<List<Book>> {
        val rules = smartRulesOf(entity)
        if (rules != null) {
            return bookDao.observePage(BookPageSql.select(smartQuery(rules)))
                .map { rows -> rows.map { row -> row.toDomain(row.authorsText.toAuthorNames()) } }
        }
        return dao.getBooksFlow(collectionId)
            .map { refs -> refs.map { it.bookId } }
            .distinctUntilChanged()
            .flatMapLatest { ids -> booksByIds(ids) }
    }

    /**
     * Правила полки, если она умная и они заданы.
     *
     * `null` означает «состав берётся из связей»: ручная полка, умная без правил или правила,
     * которые не разобрались. Пустые правила тоже `null` — полка без условий не должна
     * показывать всю библиотеку.
     */
    private fun smartRulesOf(entity: CollectionEntity?): SmartRules? {
        if (entity == null || entity.type != DatabaseCollectionType.SMART) return null
        return SmartRules.fromJson(entity.rulesJson)?.takeIf { rules -> !rules.isEmpty }
    }

    /** Запрос состава умной полки: сортировка как на полке, лимит — с запасом на домашнюю библиотеку. */
    private fun smartQuery(rules: SmartRules, pageSize: Int = SMART_PAGE_SIZE): LibraryQuery = LibraryQuery(
        sort = SortOrder.AUTHOR,
        page = 0,
        pageSize = pageSize,
        bookQuery = rules.toBookQuery(),
    )

    private fun booksByIds(ids: List<String>): Flow<List<Book>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        // DAO отдаёт сущности; имя автора лежит строкой, поэтому список разбирается здесь же.
        val flows: List<Flow<BookEntity?>> = ids.map { bookDao.observeById(it) }
        return combine(flows) { snapshot: Array<BookEntity?> ->
            snapshot.filterNotNull().map { it.toDomain(it.authorsText.toAuthorNames()) }
        }
    }

    private fun String.toAuthorNames(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Порядок задаёт пользователь, поэтому позиция растёт, а не перезаписывает список. */
    override suspend fun create(name: String, type: CollectionType): AppResult<Collection> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "name", message = "Название полки не может быть пустым"),
            )
        }
        val position = (dao.observeAll().first().maxOfOrNull { it.position } ?: -1) + 1
        val entity = CollectionEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            type = toEntityType(type),
            position = position,
            createdAt = clock.nowMillis(),
        )
        val saved = appRunCatching { dao.upsertAll(listOf(entity)) }
        return when (saved) {
            is AppResult.Failure -> saved
            is AppResult.Success -> AppResult.success(entity.toDomain())
        }
    }

    override suspend fun rename(collectionId: String, name: String): AppResult<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "name", message = "Название полки не может быть пустым"),
            )
        }
        val entity = dao.getById(collectionId) ?: return notFound()
        return appRunCatching { dao.upsertAll(listOf(entity.copy(name = trimmed))) }
    }

    /** Удаление полки каскадом уносит её книги: связи живут в `collection_books` с CASCADE. */
    override suspend fun delete(collectionId: String): AppResult<Unit> =
        appRunCatching { dao.deleteById(collectionId) }

    override suspend fun addBook(collectionId: String, bookId: String): AppResult<Unit> {
        val position = (dao.getBooks(collectionId).maxOfOrNull { it.position } ?: -1) + 1
        return appRunCatching {
            dao.insertBooks(
                listOf(
                    CollectionBookCrossRef(
                        collectionId = collectionId,
                        bookId = bookId,
                        position = position,
                        addedAt = clock.nowMillis(),
                    ),
                ),
            )
        }
    }

    override suspend fun removeBook(collectionId: String, bookId: String): AppResult<Unit> =
        appRunCatching { dao.removeBook(collectionId, bookId) }

    override suspend fun updateSmartRules(collectionId: String, rulesJson: String): AppResult<Unit> {
        val entity = dao.getById(collectionId) ?: return notFound()
        return appRunCatching { dao.upsertAll(listOf(entity.copy(rulesJson = rulesJson))) }
    }

    private fun notFound(): AppResult<Nothing> = AppResult.failure(
        AppError.NotFound(what = "collection", message = "Полка не найдена"),
    )

    private companion object {
        /**
         * Сколько книг отдаёт умная полка.
         *
         * 500 — как на самой полке: умная полка не должна молча обрезать библиотеку, а
         * отдельного листания по страницам в ней нет.
         */
        const val SMART_PAGE_SIZE = 500

        /** Сколько обложек показывает строка полки: больше в строку списка не читается. */
        const val PREVIEW_COVERS = 3
    }

    /** CollectionType описан в двух модулях; сопоставляем по имени, иначе значения не совпадут. */
    private fun toEntityType(type: CollectionType): DatabaseCollectionType =
        DatabaseCollectionType.entries.firstOrNull { it.name == type.name } ?: DatabaseCollectionType.MANUAL

    private fun CollectionEntity.toDomain() = Collection(
        id = id,
        name = name,
        type = CollectionType.entries.firstOrNull { it.name == type.name } ?: CollectionType.MANUAL,
        iconKey = iconKey,
        position = position,
        bookCount = 0,
        rulesJson = rulesJson,
    )
}
