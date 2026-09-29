package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Фейк полок для тестов.
 *
 * По правилу ADR-012 (свои типы не мокаются): вместо `mockk` — настраиваемый состав и память о
 * том, что у фейка просили. Правки (создание, переименование, удаление, добавление и снятие книги,
 * запись правил) записываются, поэтому проверка «правила сохранены» читается как утверждение о
 * данных, а не как `verify` по внутренностям.
 */
class FakeCollectionRepository(
    shelves: List<Collection> = emptyList(),
) : CollectionRepository {

    /**
     * Живой поток полок: тест вправе поменять состав уже после создания фейка (например, «полку
     * удалили, пока экран был открыт»), и экран обязан это увидеть. Снимок списка такую проверку
     * молча превратил бы в «ничего не изменилось».
     */
    val shelvesFlow: MutableStateFlow<List<Collection>> = MutableStateFlow(shelves)

    /** Полки, которые видит экран. */
    var shelves: List<Collection>
        get() = shelvesFlow.value
        set(value) {
            shelvesFlow.value = value
        }

    /** Состав полки: id полки → книги. */
    val booksByCollection: MutableMap<String, List<Book>> = mutableMapOf()

    /** Что вернуть на правки; по умолчанию успех. */
    var updateResult: AppResult<Unit> = AppResult.success(Unit)

    /** Созданные полки: имя → тип. */
    val created: MutableList<Pair<String, CollectionType>> = mutableListOf()

    /** Переименования: id → новое имя. */
    val renamed: MutableMap<String, String> = mutableMapOf()

    /** Удалённые полки. */
    val deleted: MutableList<String> = mutableListOf()

    /** Добавления книги: id полки → id книги. */
    val added: MutableList<Pair<String, String>> = mutableListOf()

    /** Снятия книги: id полки → id книги. */
    val removed: MutableList<Pair<String, String>> = mutableListOf()

    /** Последние записанные правила полки; `null` — записи не было. */
    var savedRules: Pair<String, String>? = null

    /** Сколько раз записывали правила. */
    var updateSmartRulesCalls: Int = 0
        private set

    override fun observeCollections(): Flow<List<Collection>> = shelvesFlow

    override fun observeBooks(collectionId: String): Flow<List<Book>> =
        flowOf(booksByCollection[collectionId].orEmpty())

    override suspend fun create(name: String, type: CollectionType): AppResult<Collection> {
        created += name to type
        val collection = Collection(
            id = "collection-${created.size}",
            name = name,
            type = type,
        )
        shelves = shelves + collection
        return AppResult.success(collection)
    }

    override suspend fun rename(collectionId: String, name: String): AppResult<Unit> {
        renamed[collectionId] = name
        return updateResult
    }

    override suspend fun delete(collectionId: String): AppResult<Unit> {
        deleted += collectionId
        shelves = shelves.filterNot { collection -> collection.id == collectionId }
        return updateResult
    }

    override suspend fun addBook(collectionId: String, bookId: String): AppResult<Unit> {
        added += collectionId to bookId
        return updateResult
    }

    override suspend fun removeBook(collectionId: String, bookId: String): AppResult<Unit> {
        removed += collectionId to bookId
        return updateResult
    }

    override suspend fun updateSmartRules(
        collectionId: String,
        rulesJson: String,
    ): AppResult<Unit> {
        updateSmartRulesCalls += 1
        savedRules = collectionId to rulesJson
        return updateResult
    }
}
