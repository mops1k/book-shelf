package io.github.bookshelf.core.data.tag

import io.github.bookshelf.core.database.dao.TagDao
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.TagEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись жанров/тегов книги.
 *
 * Зачем отдельный компонент: `Fb2Parser`/`EpubParser` давно кладут жанры в `BookMetadata.tags`,
 * но переносить их в базу было некому — `tags`/`book_tags` оставались пустыми, поэтому фильтр
 * и умная полка «по жанру» не находили ничего (дефект того же класса, что пустые
 * `authors`/`book_authors`).
 *
 * Идентификатор тега детерминированный (`tag:` + имя в нижнем регистре): повторный разбор
 * метаданных не плодит дубли тегов и переживает смену регистра в источнике.
 */
@Singleton
class BookTagWriter @Inject constructor(
    private val tagDao: TagDao,
) {

    /**
     * Заменить набор жанров книги.
     *
     * @param names жанры/теги как пришли из метаданных; пустые и повторы отбрасываются.
     */
    suspend fun replace(bookId: String, names: List<String>) {
        val wanted = names
            .map { name -> name.trim() }
            .filter { name -> name.isNotEmpty() }
            .distinctBy { name -> name.lowercase() }

        wanted.forEach { name ->
            val id = idOf(name)
            if (tagDao.findById(id) == null) {
                tagDao.upsertAll(listOf(TagEntity(id = id, name = name)))
            }
        }

        val linked = tagDao.getBookTags(bookId).map { ref -> ref.tagId }.toSet()
        val wantedIds = wanted.map { name -> idOf(name) }.toSet()

        (linked - wantedIds).forEach { tagId -> tagDao.removeBookTag(bookId, tagId) }

        val added = (wantedIds - linked).map { tagId -> BookTagCrossRef(bookId = bookId, tagId = tagId) }
        if (added.isNotEmpty()) {
            tagDao.insertBookTags(added)
        }
    }

    private fun idOf(name: String): String = TAG_ID_PREFIX + name.lowercase()

    private companion object {
        const val TAG_ID_PREFIX = "tag:"
    }
}
