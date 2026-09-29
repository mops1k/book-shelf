package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.repository.ReadingProgressOrigins
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.data.mapper.BookPageSql
import io.github.bookshelf.core.data.mapper.toDomain
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.MetadataOverrideDao
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.relation.BookWithRelations
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Книги: чтение страницами и изменение пользовательских полей (docs/architecture.md §3.1).
 *
 * Три решения, которые определяют поведение экрана полки:
 *
 * **1. Страница и её счётчик считаются одним конструктором** ([BookPageSql]). Условия фильтров
 * обязаны совпадать, иначе подпись «Стр. 1/12 · 47 книг» покажет несуществующее число.
 *
 * **2. Ручные правки накладываются на строку `books` при чтении** ([withOverride]) и никогда в неё
 * не пишутся. Благодаря этому пересканирование изменённого файла не затирает правку пользователя
 * (docs/architecture.md §3.1).
 *
 * **3. `UPDATE … WHERE id = …` не сообщает, затронул ли он строку.** Поэтому «книги нет» проверяется
 * отдельно ([notFoundAsFailure]): иначе команда над удалённой книгой тихо возвращала бы успех, и UI
 * показал бы «сохранено» для того, чего не произошло.
 */
@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val overrideDao: MetadataOverrideDao,
    private val readingProgressDao: ReadingProgressDao,
    private val sourceRepository: SourceRepository,
    private val scanRepository: ScanRepository,
    private val duplicateFinder: DuplicateFinder,
    private val fileRemover: SafTreeWriter,
    private val clock: AppClock,
) : BookRepository {

    override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> =
        combine(
            bookDao.observePage(BookPageSql.select(query)),
            bookDao.observeCount(BookPageSql.count(query)),
        ) { entities, total ->
            PageResult(
                items = entities.map { it.toShelfBook() },
                totalCount = total,
                offset = query.page * query.pageSize,
                limit = query.pageSize,
            )
        }

    override fun observeById(id: String): Flow<Book?> =
        bookDao.observeWithRelations(id).map { relations -> relations?.let { bookOf(it) } }

    override suspend fun getById(id: String): AppResult<Book?> {
        val relations = bookDao.getWithRelations(id)
            ?: return AppResult.failure(AppError.NotFound(what = "book", message = "Книга $id не найдена"))
        return AppResult.success(bookOf(relations))
    }

    override suspend fun updateMetadata(bookId: String, patch: MetadataPatch): AppResult<Unit> {
        if (bookDao.getById(bookId) == null) {
            return AppResult.failure(AppError.NotFound(what = "book", message = "Книга $bookId не найдена"))
        }
        return appRunCatching {
            val existing = overrideDao.getByBookId(bookId)
            overrideDao.upsert(existing.orEmptyPatch(bookId, patch, nowMillis()))
        }
    }

    override suspend fun setFavorite(bookId: String, favorite: Boolean): AppResult<Unit> =
        forExistingBook(bookId) { bookDao.setFavorite(bookId, favorite) }

    override suspend fun setRating(bookId: String, rating: Int?): AppResult<Unit> {
        if (rating != null && rating !in MIN_RATING..MAX_RATING) {
            return AppResult.failure(
                AppError.InvalidArgument(
                    what = "rating",
                    message = "Оценка должна быть от $MIN_RATING до $MAX_RATING, получено $rating",
                ),
            )
        }
        return forExistingBook(bookId) { bookDao.setRating(bookId, rating) }
    }

    override suspend fun markRead(bookId: String, status: ReadingStatus): AppResult<Unit> =
        forExistingBook(bookId) { bookDao.setReadingStatus(bookId, status.name) }

    override suspend fun findByDocumentUri(documentUri: String): AppResult<Book?> = appRunCatching {
        bookDao.getByDocumentUri(documentUri)?.toDomain(authors = emptyList())
    }

    override suspend fun saveReadingProgress(
        bookId: String,
        percent: Float,
        locatorJson: String?,
        source: String,
    ): AppResult<Unit> = forExistingBook(bookId) {
        readingProgressDao.upsert(
            ReadingProgressEntity(
                bookId = bookId,
                percent = percent.coerceIn(0f, 1f),
                locatorJson = locatorJson,
                updatedAt = clock.nowMillis(),
                source = source,
            ),
        )
    }

    override suspend fun delete(bookId: String): AppResult<Unit> =
        forExistingBook(bookId) { bookDao.deleteById(bookId) }

    override suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult> =
        appRunCatching {
            var deleted = 0
            var failed = 0
            for (bookId in bookIds) {
                val book = bookDao.getById(bookId) ?: continue
                when (fileRemover.delete(book.documentUri)) {
                    is AppResult.Failure -> failed++
                    is AppResult.Success -> {
                        bookDao.deleteById(bookId)
                        deleted++
                    }
                }
            }
            DeletionResult(deleted = deleted, failed = failed)
        }

    override suspend fun findDuplicates(): AppResult<DuplicateReport> = appRunCatching {
        duplicateFinder.find(bookDao.getAll().map { entity -> entity.toDomain(authors = emptyList()) })
    }

    /**
     * Пересканировать библиотеку: очистить таблицу и запустить скан каждой включённой папки.
     *
     * Файлы на устройстве не удаляются — команда стирает только то, что приложение само
     * записало в базу. Скан ставится на источник с политикой `KEEP`, поэтому повторное
     * нажатие кнопки не плодит дублирующие работы.
     */
    override suspend fun rescanAll(): AppResult<Unit> = appRunCatching {
        bookDao.deleteAll()
        sourceRepository.observeSources().first()
            .filter { source -> source.enabled }
            .forEach { source -> scanRepository.startScan(source.id) }
    }

    /** Книга для строки полки: авторы берутся из денормализованного `authorsText`. */
    private suspend fun BookEntity.toShelfBook(): Book =
        toDomain(authors = authorsText.toAuthorList())
            .withProgress(readingProgressDao.getByBook(bookId = id)?.percent)
            .withOverride(overrideDao.getByBookId(id))

    /**
     * Книга со связями — для карточки.
     *
     * Правка и прогресс читаются здесь же: карточка открывается на одну книгу, поэтому лишние
     * запросы не ощущаются, а данные сразу полные.
     */
    private suspend fun bookOf(relations: BookWithRelations): Book =
        relations.book
            .toDomain(authors = relations.authors.map { it.name })
            .withProgress(readingProgressDao.getByBook(bookId = relations.book.id)?.percent)
            .withOverride(overrideDao.getByBookId(relations.book.id))

    /**
     * Наложение ручной правки.
     *
     * `null` в патче означает «пользователь это поле не трогал», поэтому применяются только
     * заданные значения — так несколько частичных сохранений не затирают друг друга.
     */
    private fun Book.withOverride(override: MetadataOverrideEntity?): Book {
        if (override == null) return this
        return copy(
            title = override.title ?: title,
            subtitle = override.subtitle ?: subtitle,
            authorsText = override.authorsText ?: authorsText,
            seriesName = override.seriesName ?: seriesName,
            seriesIndex = override.seriesIndex ?: seriesIndex,
            language = override.language ?: language,
            publishedYear = override.publishedYear ?: publishedYear,
            publisher = override.publisher ?: publisher,
            description = override.description ?: description,
            pageCount = override.pageCount ?: pageCount,
        )
    }

    /**
     * Подставить прогресс чтения в книгу.
     *
     * В таблице `reading_progress` хранится доля 0..1 (так её пишет и синхронизация с читалкой, и
     * ручная правка), а наружу отдаётся процент 0..100: полка печатает значение с знаком процента
     * (`ReadingStatus.badge`), и с долей книга «на четверть прочитана» показывалась бы как «0 %».
     */
    private fun Book.withProgress(share: Float?): Book =
        if (share == null) this else copy(progressPercent = share * PERCENT)

    /**
     * Выполнить команду, только если книга существует.
     *
     * Существование проверяется **до** операции, а не по её результату: `UPDATE` и `DELETE` не
     * сообщают, затронули ли они строку, поэтому после `DELETE` проверка всегда дала бы «не
     * найдена» и команда удаления всегда «падала» бы. Кроме того, так команда над удалённой
     * книгой честно отвечает «не найдено», а не «успешно».
     */
    private suspend inline fun forExistingBook(
        bookId: String,
        crossinline action: suspend () -> Unit,
    ): AppResult<Unit> {
        if (bookDao.getById(bookId) == null) {
            return AppResult.failure(AppError.NotFound(what = "book", message = "Книга $bookId не найдена"))
        }
        return appRunCatching { action() }
    }

    private companion object {
        const val MIN_RATING = 0
        const val MAX_RATING = 5

        /** Доля в таблице прогресса (0..1) → проценты для полки (0..100). */
        const val PERCENT = 100f

        /**
         * Время сохранения правки. Нужно только для показа «изменено вручную», поэтому системных
         * часов достаточно; внедрение [io.github.bookshelf.core.common.time.AppClock] придёт
         * вместе с тестами, которым понадобится управлять временем.
         */
        fun nowMillis(): Long = System.currentTimeMillis()
    }
}

/** `authorsText` — денормализованная строка «Автор, Автор»; пустые хвосты отбрасываются. */
private fun String.toAuthorList(): List<String> =
    split(AUTHORS_SEPARATOR_LOCAL).map { it.trim() }.filter { it.isNotEmpty() }

private const val AUTHORS_SEPARATOR_LOCAL = ","

/** Наложение патча на существующую строку переопределения: заданные поля обновляются, остальные сохраняются. */
private fun MetadataOverrideEntity?.orEmptyPatch(
    bookId: String,
    patch: MetadataPatch,
    updatedAt: Long,
): MetadataOverrideEntity = MetadataOverrideEntity(
    bookId = bookId,
    title = patch.title ?: this?.title,
    subtitle = patch.subtitle ?: this?.subtitle,
    authorsText = patch.authorsText ?: this?.authorsText,
    seriesName = patch.seriesName ?: this?.seriesName,
    seriesIndex = patch.seriesIndex ?: this?.seriesIndex,
    language = patch.language ?: this?.language,
    publishedYear = patch.publishedYear ?: this?.publishedYear,
    publisher = patch.publisher ?: this?.publisher,
    description = patch.description ?: this?.description,
    isbn = patch.isbn ?: this?.isbn,
    pageCount = patch.pageCount ?: this?.pageCount,
    updatedAt = updatedAt,
)
