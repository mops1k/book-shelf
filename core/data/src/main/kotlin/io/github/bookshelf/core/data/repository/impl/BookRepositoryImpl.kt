package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.author.parseAuthors
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
import io.github.bookshelf.core.metadata.model.BookMetadata
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
 * **2. Ручная правка пишется и в строку `books`, и в `metadata_overrides`.** В `books` — потому что
 * её читают все запросы: полка, обзор серий, фасеты и поиск (FTS синхронизируется триггерами Room),
 * а не только карточка. В `metadata_overrides` — как страховка: скан изменённого файла перезапишет
 * строку метаданными файла, и правку нужно вернуть ([withOverride] накладывает её при чтении).
 * Решение Александра, 29.09.2026: правка должна переживать перенос файла на другое устройство,
 * поэтому те же значения записываются ещё и в сам файл книги (docs/architecture.md §3.1).
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
    /** Правка авторов должна попадать в связи: по ним работает всё авторское измерение. */
    private val authorWriter: BookAuthorWriter,
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
        val book = bookDao.getById(bookId)
            ?: return AppResult.failure(AppError.NotFound(what = "book", message = "Книга $bookId не найдена"))
        return appRunCatching {
            val now = nowMillis()
            val existing = overrideDao.getByBookId(bookId)
            val updated = existing.orEmptyPatch(bookId, patch, now)
            overrideDao.upsert(updated)
            // Правка пишется и в строку books: её читают полка, обзор серий, фасеты и поиск.
            // Строка в metadata_overrides остаётся страховкой на случай скана изменённого файла.
            val mergedTitle = updated.title ?: book.title
            val mergedAuthors = updated.authorsText ?: book.authorsText
            // Строка записывается целиком через существующий `update`: отдельный запрос с восемью
            // параметрами нарушал порог detekt, а новый DAO тянул за собой ещё одну зависимость
            // репозитория (у него их уже девять при пороге десять). Строка прочитана строкой выше,
            // поэтому терять чужие изменения нечему: скан пишет только изменившиеся файлы.
            bookDao.update(
                book.copy(
                    title = mergedTitle,
                    // Заголовок для сортировки пересчитывается вместе с названием: иначе «The War
                    // and Peace» осталось бы на месте «Войны и мира».
                    sortTitle = BookMetadata(title = mergedTitle).sortTitleOf(),
                    authorsText = mergedAuthors,
                    // Пустая строка в правке — «значение убрали»: в базе это NULL, иначе полка
                    // показывала бы пустую серию вместо её отсутствия.
                    seriesName = (updated.seriesName ?: book.seriesName).withoutEmpty(),
                    // Номер серии очищается по флагу: у `Double?` нет «пустого» значения.
                    seriesIndex = if (patch.clearSeriesIndex) {
                        null
                    } else {
                        updated.seriesIndex ?: book.seriesIndex
                    },
                    description = (updated.description ?: book.description).withoutEmpty(),
                ),
            )
            // Авторское измерение читает связи, а не строку `books.authorsText`, поэтому правка
            // авторов обязана переписать и их. Иначе карточка показывала нового автора, а фильтр
            // по нему не находил ни одной книги (дефект того же класса, что и весь этот переход).
            // Берётся итоговая строка: правка могла авторов не трогать — тогда действует прежняя.
            authorWriter.replace(bookId, mergedAuthors)
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
        toDomain(authors = parseAuthors(authorsText))
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
            subtitle = override.subtitle.overrideOr(subtitle),
            authorsText = override.authorsText ?: authorsText,
            // `""` в правке означает «значение убрали»: подставлять значение из файла нельзя,
            // иначе стёртая серия вернулась бы на экран.
            seriesName = override.seriesName.overrideOr(seriesName),
            seriesIndex = override.seriesIndex ?: seriesIndex,
            language = override.language.overrideOr(language),
            publishedYear = override.publishedYear ?: publishedYear,
            publisher = override.publisher.overrideOr(publisher),
            description = override.description.overrideOr(description),
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


/**
 * Значение правки поверх значения из файла.
 *
 * `null` — поле не трогали, берётся значение файла; пустая строка — значение убрали, и подставлять
 * файловое нельзя; иначе — значение пользователя.
 */
private fun String?.overrideOr(fileValue: String?): String? = when {
    this == null -> fileValue
    isEmpty() -> null
    else -> this
}

/** Пустая строка при записи в `books` становится `NULL`: «серии нет», а не «серия без названия». */
private fun String?.withoutEmpty(): String? = if (isNullOrEmpty()) null else this

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
    seriesIndex = when {
        patch.clearSeriesIndex -> null
        else -> patch.seriesIndex ?: this?.seriesIndex
    },
    language = patch.language ?: this?.language,
    publishedYear = patch.publishedYear ?: this?.publishedYear,
    publisher = patch.publisher ?: this?.publisher,
    description = patch.description ?: this?.description,
    isbn = patch.isbn ?: this?.isbn,
    pageCount = patch.pageCount ?: this?.pageCount,
    updatedAt = updatedAt,
)
