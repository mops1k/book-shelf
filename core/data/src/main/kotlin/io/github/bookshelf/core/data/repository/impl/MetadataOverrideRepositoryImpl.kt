package io.github.bookshelf.core.data.repository.impl

import android.database.sqlite.SQLiteConstraintException
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.data.model.MetadataOverride
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.repository.MetadataOverrideRepository
import io.github.bookshelf.core.database.dao.MetadataOverrideDao
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Хранение ручных правок метаданных (docs/architecture.md §3.1, §4.4).
 *
 * Репозиторий **не трогает `books`**. Это не оптимизация, а требование: пересканирование
 * изменённого файла перезаписывает встроенные метаданные, и правка пользователя, лежащая в
 * `books`, затерлась бы вместе с ними. Слияние «override → метаданные файла → имя файла»
 * выполняет вызывающий код, читая обе таблицы.
 *
 * Исключения Room наружу не пробрасываются: они превращаются в [AppError], как требует правило
 * проекта (docs/architecture.md §1).
 */
@Singleton
class MetadataOverrideRepositoryImpl @Inject constructor(
    private val dao: MetadataOverrideDao,
) : MetadataOverrideRepository {

    override fun observe(bookId: String): Flow<MetadataOverride?> =
        dao.observe(bookId).map { it?.toOverrideModel() }

    override suspend fun save(override: MetadataOverride): AppResult<Unit> =
        appRunCatching { dao.upsert(override.toEntity()) }.asMissingBookIfUnknown(override.bookId)

    override suspend fun clear(bookId: String): AppResult<Unit> = appRunCatching {
        dao.clear(bookId)
    }

    /**
     * Нарушение ограничения при вставке означает, что книги нет.
     *
     * `SQLiteConstraintException` приходит и за нарушение внешнего ключа, и за `NOT NULL`, а по
     * типу их не различить, поэтому существование книги проверяется отдельно. Так «пользователь
     * правит несуществующую книгу» (ошибка) отличается от «у книги ещё нет правки» (пусто).
     */
    private suspend fun AppResult<Unit>.asMissingBookIfUnknown(bookId: String): AppResult<Unit> {
        val error = (this as? AppResult.Failure)?.error
        val isConstraintViolation = error is AppError.Unknown && error.cause is SQLiteConstraintException
        return if (isConstraintViolation && dao.getByBookId(bookId) == null) {
            AppResult.failure(AppError.NotFound(what = "book", message = "Книга $bookId не найдена"))
        } else {
            this
        }
    }
}

/** Переопределение → строка таблицы. */
private fun MetadataOverride.toEntity() = MetadataOverrideEntity(
    bookId = bookId,
    title = patch.title,
    subtitle = patch.subtitle,
    authorsText = patch.authorsText,
    seriesName = patch.seriesName,
    seriesIndex = patch.seriesIndex,
    language = patch.language,
    publishedYear = patch.publishedYear,
    publisher = patch.publisher,
    description = patch.description,
    isbn = patch.isbn,
    pageCount = patch.pageCount,
    updatedAt = updatedAt,
)

/** Строка таблицы → доменная модель. Отсутствующие поля патча остаются `null`, а не пустыми строками. */
private fun MetadataOverrideEntity.toOverrideModel() = MetadataOverride(
    bookId = bookId,
    patch = MetadataPatch(
        title = title,
        subtitle = subtitle,
        authorsText = authorsText,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        language = language,
        publishedYear = publishedYear,
        publisher = publisher,
        description = description,
        isbn = isbn,
        pageCount = pageCount,
    ),
    updatedAt = updatedAt,
)
