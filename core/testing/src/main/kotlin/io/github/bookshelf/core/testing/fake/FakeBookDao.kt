package io.github.bookshelf.core.testing.fake

import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.relation.BookWithRelations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf

/**
 * Фейк `BookDao` для тестов репозиториев.
 *
 * По правилу ADR-012 свои интерфейсы в тестах не мокаются. Здесь настраиваются только те три
 * запроса, которые репозитории полок реально читают ([observePage], [observeCount], [observeById]),
 * а остальные методы честно падают: если тест неожиданно пойдёт другим путём, это будет видно сразу,
 * а не превратится в тихий `null` от `relaxed`-мока.
 *
 * Счётчики обращений ([pageCalls], [countCalls]) заменяют `verify { ... }`: по ним видно, запросил
 * ли экран страницу и сколько раз.
 */
class FakeBookDao(
    var pageFlow: Flow<List<BookEntity>> = flowOf(emptyList()),
    var countFlow: Flow<Int> = flowOf(0),
    var byIdFlow: Flow<BookEntity?> = MutableStateFlow(null),
) : BookDao {

    /** Сколько раз у фейка просили страницу книг. */
    var pageCalls: Int = 0
        private set

    /** Сколько раз у фейка просили число книг. */
    var countCalls: Int = 0
        private set

    override fun observePage(query: SupportSQLiteQuery): Flow<List<BookEntity>> {
        pageCalls += 1
        return pageFlow
    }

    override fun observeCount(query: SupportSQLiteQuery): Flow<Int> {
        countCalls += 1
        return countFlow
    }

    override fun observeById(id: String): Flow<BookEntity?> = byIdFlow

    override suspend fun insert(book: BookEntity) = notUsed("insert")
    override suspend fun upsertAll(books: List<BookEntity>) = notUsed("upsertAll")
    override suspend fun update(book: BookEntity) = notUsed("update")
    /**
     * Поиск копии книги.
     *
     * Отвечает по [byIdFlow] — единственной книге, которую фейк знает: тесты полок о копиях не
     * спрашивают, а если спросят, поведение должно быть предсказуемым, а не падать.
     */
    override suspend fun countByFileNameAndSize(
        fileName: String,
        sizeBytes: Long,
        excludeId: String,
    ): Int {
        val book = byIdFlow.firstOrNull() ?: return 0
        val same = book.fileName == fileName && book.sizeBytes == sizeBytes && book.id != excludeId
        return if (same) 1 else 0
    }

    override suspend fun getAll(): List<BookEntity> = notUsed("getAll")
    override suspend fun deleteById(id: String) = notUsed("deleteById")
    override suspend fun deleteAll() = notUsed("deleteAll")
    override suspend fun getById(id: String): BookEntity? = notUsed("getById")
    override suspend fun findByDocumentUri(documentUri: String): BookEntity? = notUsed("findByDocumentUri")
    override suspend fun touchLastSeen(ids: List<String>, seenAt: Long) = notUsed("touchLastSeen")
    override suspend fun markMissing(ids: List<String>, since: Long) = notUsed("markMissing")
    override suspend fun idsBySource(sourceId: String): List<String> = notUsed("idsBySource")
    override suspend fun countBySource(sourceId: String): Int = notUsed("countBySource")
    override suspend fun count(query: SupportSQLiteQuery): Int = notUsed("count")
    override suspend fun setFavorite(id: String, favorite: Boolean) = notUsed("setFavorite")
    override suspend fun setRating(id: String, rating: Int?) = notUsed("setRating")
    override suspend fun setReadingStatus(id: String, status: String) = notUsed("setReadingStatus")
    override suspend fun getQuickHash(id: String): String? = notUsed("getQuickHash")
    override suspend fun getWithRelations(id: String): BookWithRelations? = notUsed("getWithRelations")
    override fun observeWithRelations(id: String): Flow<BookWithRelations?> = notUsed("observeWithRelations")
    override suspend fun idsWithoutTags(): List<String> = notUsed("idsWithoutTags")
}

/**
 * Заглушка для методов, которые тесты полок не читают.
 *
 * Падает, а не возвращает пустоту: если тест неожиданно пойдёт этим путём, причина будет видна
 * сразу — `relaxed`-мок в такой ситуации молча отдал бы `null`.
 */
private fun notUsed(method: String): Nothing = error("BookDao.$method в этом тесте не нужен")
