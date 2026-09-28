package io.github.bookshelf.core.database

import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity

/**
 * Общие заготовки сущностей для тестов базы.
 *
 * Вынесены, потому что `BookDaoTest`, `MetadataOverrideCascadeTest` и будущие тесты репозиториев
 * собирают одну и ту же книгу; копия полного конструктора `BookEntity` в каждом файле только
 * разъезжалась бы при добавлении поля в схему.
 */
internal object BookDaoTestFixtures {

    fun book(
        id: String,
        sourceId: String,
        title: String = "Тестовая книга",
        author: String = "Иван Петров",
        series: String? = null,
        extension: String = "fb2",
        sizeBytes: Long = 1024L,
        addedAt: Long = 1000L,
    ) = BookEntity(
        id = id,
        sourceId = sourceId,
        documentUri = "content://com.android.externalstorage.documents/tree/books/document/$id",
        relativePath = "Петров/$title.$extension",
        fileName = "$title.$extension",
        extension = extension,
        mimeType = "application/$extension",
        sizeBytes = sizeBytes,
        lastModified = 1000L,
        title = title,
        sortTitle = title,
        authorsText = author,
        seriesName = series,
        addedAt = addedAt,
        updatedAt = addedAt,
        lastSeenAt = addedAt,
    )

    fun source(id: String, name: String = "Books") = SourceEntity(
        id = id,
        uri = "content://com.android.externalstorage.documents/tree/books",
        displayName = name,
        kind = io.github.bookshelf.core.database.model.SourceKind.SAF_TREE,
    )
}
