package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.author.parseAuthors
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.data.repository.BookOptimizationResult
import io.github.bookshelf.core.fb2opt.Fb2Optimizer
import io.github.bookshelf.core.storage.files.BookContentOpener
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
import io.github.bookshelf.core.data.model.BookCopy
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.database.dao.BookCleanupDao
import io.github.bookshelf.core.database.dao.BookCopyDao
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.MetadataOverrideDao
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.relation.BookWithRelations
import io.github.bookshelf.core.fb2opt.Fb2Optimization
import io.github.bookshelf.core.metadata.model.BookMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Книги: чтение страницами и изменение пользовательских полей («Архитектура» §3.1).
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
 * поэтому те же значения записываются ещё и в сам файл книги («Архитектура» §3.1).
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
    /** Сессия чтения: тяжёлые работы (поиск дублей) уступают читалке. */
    private val readerSession: ReaderSession,
    private val fileRemover: SafTreeWriter,
    private val clock: AppClock,
    /** Правка авторов должна попадать в связи: по ним работает всё авторское измерение. */
    private val authorWriter: BookAuthorWriter,
    /**
     * Удаление книги вместе со связями.
     *
     * `BookDao.deleteById` убирал только строку `books`, а теги, авторы, полки, прогресс и кэш
     * обложек оставались сиротами: на устройстве нашлись 19 строк `book_tags` от 9 удалённых книг.
     */
    private val bookCleanupDao: BookCleanupDao,
    /**
     * Связи «файл-копия → книга».
     *
     * Нужны удалению: копия — тот же файл, и когда книгу убирают с устройства, её копии обязаны
     * уйти вместе с ней, иначе на диске навсегда остаётся файл, который никому не принадлежит
     * (решение Александра 01.10.2026). Прогресс при этом не теряется: он у книги, которую удаляют.
     */
    private val bookCopyDao: BookCopyDao,
    /** Доступ к содержимому книги: чтение и безопасная замена файла. */
    private val contentOpener: BookContentOpener,
    /** Оптимизация FB2 без потери данных. */
    private val fb2Optimizer: Fb2Optimizer,
    /**
     * Диспетчеры приложения.
     *
     * Нужны потому, что часть работы здесь — обычное блокирующее чтение файлов и счёт на процессоре.
     * Вызывают эти методы из `viewModelScope` (это главный поток), поэтому без переключения
     * интерфейс встаёт: 30.09.2026 оптимизация книги так и привела к ANR карточки.
     */
    private val dispatchers: AppDispatchers,
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
            val now = clock.nowMillis()
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
        bookDao.findByDocumentUri(documentUri)?.toDomain(authors = emptyList())
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
        // Удаление файлов идёт через SAF и блокирует поток: на списке из десятков книг это
        // заметная пауза, поэтому работа уходит на фоновый диспетчер.
        withContext(dispatchers.io) {
            appRunCatching {
                var deleted = 0
                var failed = 0
                for (bookId in bookIds) {
                    val book = bookDao.getById(bookId) ?: continue
                    when (fileRemover.delete(book.documentUri)) {
                        is AppResult.Failure -> failed++
                        is AppResult.Success -> {
                            // Копии удаляются до строки книги: после `deleteWithLinks` связь
                            // снимется каскадом, и найти их будет уже нечем.
                            deleteCopiesOf(bookId)
                            bookCleanupDao.deleteWithLinks(listOf(bookId))
                            deleted++
                        }
                    }
                }
                DeletionResult(deleted = deleted, failed = failed)
            }
        }

    /**
     * Убрать с устройства файлы-копии книги и их связи.
     *
     * Отказ провайдера не отменяет удаление книги: связь снимет каскад, а файл останется на диске —
     * и следующий скан заведёт по нему книгу заново. Это честнее, чем скрывать файл навсегда: если
     * удалить не дали, человек должен увидеть книгу, а не невидимый мусор.
     */
    private suspend fun deleteCopiesOf(bookId: String) {
        bookCopyDao.getByBookId(bookId).forEach { copy ->
            fileRemover.delete(copy.copyDocumentUri)
            bookCopyDao.deleteByUri(copy.copyDocumentUri)
        }
    }

    /**
     * Оптимизация книги FB2: файл заменяется уменьшенным, если выигрыш есть.
     *
     * Порядок и причины:
     * 1. книгу уже проверяли и файл с тех пор не менялся — не перечитываем её вовсе;
     * 2. читалка открыта — отказываемся: оптимизация тяжёлая и не должна конкурировать с чтением;
     * 3. читаем файл целиком: он нужен и для оптимизации, и как страховка на случай сбоя записи;
     * 4. заменяем содержимое документа в режиме усечения (переименование SAF не поддерживает);
     * 5. запись не удалась — возвращаем исходные байты на место, иначе книга осталась бы битой.
     *
     * Отметка проверки ставится и когда выигрыша не было: смысл её в том, что «эту версию файла
     * смотреть больше нечего», а не в том, что файл стал меньше.
     */
    override suspend fun optimizeBook(bookId: String): AppResult<BookOptimizationResult> =
        withContext(dispatchers.io) { optimizeBookOnBackground(bookId) }

    private suspend fun optimizeBookOnBackground(bookId: String): AppResult<BookOptimizationResult> {
        val entity = bookDao.getById(bookId)
            ?: return AppResult.failure(
                AppError.NotFound(what = "book", message = "Книга $bookId не найдена"),
            )
        val extension = entity.extension.lowercase()
        if (extension !in OPTIMIZABLE_EXTENSIONS) {
            return AppResult.failure(
                AppError.InvalidArgument(
                    what = "extension",
                    message = "Оптимизируются только книги FB2 и FB2.ZIP, а это «${entity.extension}»",
                ),
            )
        }
        if (entity.optimizedAt != null) return AppResult.success(alreadyOptimizedResult(entity))
        if (readerSession.isActive()) {
            return AppResult.failure(
                AppError.PermissionDenied(
                    message = "Открыта читалка — оптимизация подождёт, пока вы вернётесь в приложение",
                ),
            )
        }
        val original = contentOpener.openStream(entity.documentUri)?.use { stream -> stream.readBytes() }
            ?: return AppResult.failure(AppError.Io(message = "Не удалось прочитать файл книги"))
        val context = currentCoroutineContext()
        return try {
            val optimization = fb2Optimizer.optimize(entity.fileName, original) { !context.isActive }
            // Отменённый прогон ничего не записывает и ничего не помечает: книга остаётся такой,
            // какой была, и её можно будет обработать в следующий раз.
            context.ensureActive()
            val optimized = optimization.bytes
            if (!optimization.changed || optimized == null) {
                markChecked(entity)
                AppResult.success(
                    BookOptimizationResult(
                        savedBytes = 0,
                        beforeBytes = optimization.beforeBytes,
                        afterBytes = optimization.beforeBytes,
                        images = 0,
                    ),
                )
            } else {
                replaceOptimizedFile(
                    entity = entity,
                    original = original,
                    optimization = optimization,
                    optimized = optimized,
                )
            }
        } catch (error: IllegalStateException) {
            AppResult.failure(AppError.Unknown(message = "Оптимизация книги не удалась", cause = error))
        }
    }

    /** Отметить книгу как проверенную: эту версию файла смотреть больше нечего. */
    private suspend fun markChecked(entity: BookEntity) {
        bookDao.update(entity.copy(optimizedAt = clock.nowMillis()))
    }

/** Результат «файл уже оптимизирован»: размеры равны, экономии нет, отметка стоит. */
private fun alreadyOptimizedResult(entity: BookEntity): BookOptimizationResult = BookOptimizationResult(
    savedBytes = 0,
    beforeBytes = entity.sizeBytes.toInt(),
    afterBytes = entity.sizeBytes.toInt(),
    images = 0,
    alreadyOptimized = true,
)

    /**
     * Записать оптимизированный файл на место и обновить факты о нём.
     *
     * Откат при отказе провайдера обязателен: файл уже мог быть усечён, поэтому исходные байты
     * возвращаются на место. Размер и время изменения записываются вместе с отметкой `optimizedAt`:
     * иначе скан увидел бы расхождение с файлом, перечитал бы метаданные и снял отметку проверки.
     *
     * Вынесено из `optimizeBookOnBackground` отдельной функцией: та перевалила за предел длины
     * detekt, а замена файла — цельный кусок со своей страховкой.
     */
    private suspend fun replaceOptimizedFile(
        entity: BookEntity,
        original: ByteArray,
        optimization: Fb2Optimization,
        optimized: ByteArray,
    ): AppResult<BookOptimizationResult> {
        val replaced = contentOpener.replaceContent(entity.documentUri, optimized)
        return if (replaced is AppResult.Failure) {
            // Откат: файл уже мог быть усечён, поэтому возвращаем исходные байты на место.
            contentOpener.replaceContent(entity.documentUri, original)
            AppResult.failure(replaced.error)
        } else {
            // Размер и время изменения записываются сразу: иначе скан увидел бы расхождение
            // с файлом, перечитал бы метаданные, сбросил обложку и снял отметку проверки —
            // а книга-то изменилась только нами. Чего провайдер не сообщает, то остаётся
            // прежним: выдуманное время заставило бы скан поверить в несуществующее.
            val facts = contentOpener.factsOf(entity.documentUri)
            bookDao.update(
                entity.copy(
                    sizeBytes = facts?.sizeBytes ?: optimization.afterBytes.toLong(),
                    lastModified = facts?.lastModified ?: entity.lastModified,
                    optimizedAt = clock.nowMillis(),
                ),
            )
            AppResult.success(
                BookOptimizationResult(
                    savedBytes = optimization.savedBytes,
                    beforeBytes = optimization.beforeBytes,
                    afterBytes = optimization.afterBytes,
                    images = optimization.images,
                ),
            )
        }
    }

    /**
     * Оптимизировать все книги FB2 и FB2.ZIP.
     *
     * Правила пакетной работы, которых нет у одиночной:
     * 1. **Читалка главнее.** Открытая читалка не просто отказывает одной книге — прогон
     *    останавливается целиком и помечается `stoppedByReader`: иначе человек увидел бы
     *    «готово», хотя обработана половина.
     * 2. **Сбой одной книги не срывает остальные.** Битый или недоступный файл попадает в
     *    `failed`, и перебор продолжается: из-за одной книги отказывать во всей библиотеке нельзя.
     * 3. **Отмена — отменой корутины.** Проверка `ensureActive()` в начале каждой итерации
     *    гарантирует, что прогон остановится между книгами, а не в середине записи файла.
     */
    override suspend fun optimizeAllFb2(
        onProgress: (done: Int, total: Int) -> Unit,
    ): AppResult<BookOptimizationBatch> = withContext(dispatchers.io) {
        val optimizable = bookDao.getAll().filter { entity ->
            entity.extension.lowercase() in OPTIMIZABLE_EXTENSIONS
        }
        // Книги, которые уже проверяли и файл которых не менялся, в работу не берутся: иначе
        // повторное нажатие кнопки заново пересобирало бы всю библиотеку (жалоба Александра
        // 30.09.2026). Отметка снимается сканом, когда файл действительно изменился.
        val candidates = optimizable.filter { entity -> entity.optimizedAt == null }
        val alreadyOptimized = optimizable.size - candidates.size
        var optimized = 0
        var skipped = 0
        var failed = 0
        var savedBytes = 0L
        var stoppedByReader = false
        var done = 0
        onProgress(done, candidates.size)
        var index = 0
        // Цикл без `break`: остановка — это условие продолжения, и так видно, что после открытия
        // читалки не обрабатывается ни одна следующая книга.
        while (index < candidates.size && !stoppedByReader) {
            // Отмена проверяется до чтения файла: прерывать запись на середине нельзя.
            currentCoroutineContext().ensureActive()
            if (readerSession.isActive()) {
                stoppedByReader = true
            } else {
                val stop = when (val outcome = optimizeBook(candidates[index].id)) {
                    is AppResult.Failure -> if (outcome.error is AppError.PermissionDenied) {
                        true
                    } else {
                        failed++
                        false
                    }

                    is AppResult.Success -> {
                        val result = outcome.value
                        if (result.changed) {
                            optimized++
                            savedBytes += result.savedBytes
                        } else {
                            skipped++
                        }
                        false
                    }
                }
                if (stop) {
                    stoppedByReader = true
                } else {
                    done++
                    onProgress(done, candidates.size)
                }
            }
            index++
        }
        AppResult.success(
            BookOptimizationBatch(
                total = candidates.size,
                optimized = optimized,
                skipped = skipped,
                failed = failed,
                savedBytes = savedBytes,
                stoppedByReader = stoppedByReader,
                alreadyOptimized = alreadyOptimized,
            ),
        )
    }

    /**
     * Книги с ошибкой разбора — одним запросом.
     *
     * Ограничение [PARSE_ERROR_LIMIT] осознанное: это список для человека, а не выгрузка. Причина
     * читается из `books.parseError`, куда её кладёт разбор файла.
     */
    override suspend fun booksWithParseErrors(): AppResult<List<Book>> = withContext(dispatchers.io) {
        appRunCatching {
            bookDao.booksWithParseErrors(PARSE_ERROR_LIMIT)
                .map { entity -> entity.toDomain(authors = parseAuthors(entity.authorsText)) }
        }
    }

    override fun copies(): Flow<List<BookCopy>> = bookCopyDao.observeAllWithTitles().map { rows ->
        rows.map { row ->
            BookCopy(
                copyDocumentUri = row.copyDocumentUri,
                bookId = row.bookId,
                bookTitle = row.title,
                sizeBytes = row.sizeBytes,
            )
        }
    }

    override suspend fun removeCopies(copyUris: List<String>): AppResult<DeletionResult> =
        withContext(dispatchers.io) {
            appRunCatching {
                var deleted = 0
                var failed = 0
                for (uri in copyUris) {
                    if (bookCopyDao.findByUri(uri) == null) continue
                    when (fileRemover.delete(uri)) {
                        is AppResult.Failure -> failed++
                        is AppResult.Success -> {
                            bookCopyDao.deleteByUri(uri)
                            deleted++
                        }
                    }
                }
                DeletionResult(deleted = deleted, failed = failed)
            }
        }

    override suspend fun findDuplicates(): AppResult<DuplicateReport> = withContext(dispatchers.io) {
        appRunCatching {
            // Полный хеш файлов — самая тяжёлая работа в приложении, и она уступает читалке:
            // открылась читалка — поиск останавливается и честно говорит об этом, а не молчит
            // «дублей нет». Идёт это на фоновом диспетчере: чтение файлов блокирующее.
            duplicateFinder.find(
                books = bookDao.getAll().map { entity -> entity.toDomain(authors = emptyList()) },
                shouldStop = { readerSession.isActive() },
            )
        }
    }

    /**
     * Пересканировать библиотеку: запустить скан каждой включённой папки.
     *
     * Таблица книг **не очищается**. Раньше команда начиналась с `deleteAll()` («забудь всё и
     * собери заново», решение 28.09.2026), и это оказалось опасным: 30.09.2026 прогон упал на
     * `UNIQUE books.documentUri`, а полка осталась пустой — 79 книг исчезли из базы, пока
     * следующий прогон не собрал их заново (решение Александра 30.09.2026 — очистку убрать).
     *
     * Отдельная очистка и не нужна: скан сам удаляет книги, которых больше нет на диске
     * (после успешного полного обхода), и переразбирает изменённые файлы.
     *
     * Файлы на устройстве не удаляются. Скан ставится на источник с политикой `KEEP`, поэтому
     * повторное нажатие кнопки не плодит дублирующие работы.
     */
    override suspend fun rescanAll(): AppResult<Unit> = appRunCatching {
        sourceRepository.observeSources().first()
            .filter { source -> source.enabled }
            .forEach { source -> scanRepository.startScan(source.id, restart = true) }
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

/** Сколько проблемных файлов показывать в отчёте: это список для человека, а не выгрузка. */
private const val PARSE_ERROR_LIMIT = 200

/** Расширения, которые оптимизируются: только формат FB2 (в том числе в архиве). */
private val OPTIMIZABLE_EXTENSIONS = setOf("fb2", "fb2.zip")
