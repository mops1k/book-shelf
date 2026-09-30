package io.github.bookshelf.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.data.book.ParsedBookWriter
import io.github.bookshelf.core.database.model.MetadataSource
import io.github.bookshelf.core.database.model.ParseState
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.parser.parseSafely
import io.github.bookshelf.core.metadata.registry.ParserRegistry
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.files.readRange
import java.io.File
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Разбор метаданных и обложек одной книги (docs/architecture.md §4.4).
 *
 * Работа вызывается по одной книге, а не пачкой на библиотеку: у каждой книги свой приоритет,
 * и видимая на полке разбирается раньше той, что лежит в конце списка. Так полка наполняется
 * сразу, а не через несколько минут после полного скана.
 *
 * **Ошибка разбора — это состояние книги, а не падение работы.** Битый файл должен показать
 * метку «не удалось разобрать», а не ронять очередь на всех остальных книгах. Поэтому
 * [ParseResult.Failed] пишется в `parseState = FAILED` и даёт `Result.success()`
 * (docs/architecture.md §1: библиотека не бросает исключения наружу).
 */
@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val bookDao: BookDao,
    private val registry: ParserRegistry,
    private val coverCache: CoverCache,
    private val contentOpener: BookContentOpener,
    private val clock: AppClock,
    /**
     * Запись результата разбора: строка книги, жанры и связи авторов.
     *
     * Связи авторов пишутся здесь же: разбор — основной путь появления авторов после скана,
     * а авторское измерение (фасет, фильтр, серии) читает связи. Раньше строку книги и жанры
     * писали врозь, и авторы не попадали в `authors`/`book_authors` вовсе.
     */
    private val parsedBookWriter: ParsedBookWriter,
    /** Сессия чтения: разбор — тяжёлая работа и не должен конкурировать с читалкой. */
    private val readerSession: ReaderSession,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        // Открыта читалка — разбор откладываем. `retry` здесь честный: работа не потеряна, книга
        // остаётся в состоянии PENDING и будет разобрана, когда чтение закончится. `success` был бы
        // хуже: книга осталась бы неразобранной до следующего скана, которого может не быть.
        if (readerSession.isActive()) {
            Log.w("BookShelf", "разбор отложен: открыта читалка")
            return Result.retry()
        }
        val book = bookDao.getById(bookId) ?: return Result.success()
        // Принудительный разбор нужен бэкфиллу: у старых книг parseState уже OK, но жанров
        // в базе нет — обычный путь их пропускает, и жанры не появились бы никогда.
        val force = inputData.getBoolean(KEY_FORCE, false)
        // Перерисовка обложки — единственный случай, когда разбор запускается ради картинки.
        val refreshCover = inputData.getBoolean(KEY_REFRESH_COVER, false)
        if (book.parseState == ParseState.OK && !force && !refreshCover) return Result.success()

        currentCoroutineContext().ensureActive()
        val parser = registry.select(book.extension, book.mimeType.ifEmpty { null }, EMPTY_PREFIX)
        val input = book.inputOf()

        // `parseSafely`, а не прямой вызов: заглушка формата или чужой парсер могут бросить,
        // и исключение не должно валить фоновую работу (книга получит причину в parseError).
        return when (val parsed = parseSafely(parser, input)) {
            is ParseResult.Success -> {
                // Обложка приходит из того же прохода, что и метаданные: отдельный
                // CoverExtractor был бы вторым чтением файла и лишней точкой отказа.
                val coverPath = chooseCoverPath(
                    cachedCover = cachedCoverOf(book),
                    hasExtractedCover = parsed.cover != null,
                    refreshCover = refreshCover,
                    store = { parsed.cover?.let { cover -> storeCover(book.id, cover) } },
                )
                parsedBookWriter.store(
                    updated = book.applyParsed(parsed.metadata, coverPath, clock.nowMillis()),
                    tags = parsed.metadata.tags,
                )
                Result.success(workDataOf(KEY_BOOK_ID to bookId))
            }

            is ParseResult.Partial -> {
                parsedBookWriter.store(
                    updated = book.applyParsed(parsed.metadata, null, clock.nowMillis()),
                    tags = parsed.metadata.tags,
                )
                Result.success(workDataOf(KEY_BOOK_ID to bookId))
            }

            is ParseResult.Failed -> {
                bookDao.update(book.applyFailed(parsed.reason, clock.nowMillis()))
                Result.success()
            }

            is ParseResult.Unsupported -> {
                bookDao.update(
                    book.copy(
                        parseState = ParseState.FAILED,
                        parseError = NO_PARSER,
                        updatedAt = clock.nowMillis(),
                    ),
                )
                Result.success()
            }
        }
    }

    /**
     * Обложка кладётся в дисковый кэш, а в книгу пишется путь к файлу.
     *
     * Путь, а не байты: байты в таблице `books` раздули бы её на десятки мегабайт, а кэш всё
     * равно нужен самому себе — иначе обложка декодировалась бы заново при каждом показе.
     */
    private suspend fun storeCover(bookId: String, cover: CoverBytes): String? =
        coverCache.store(bookId, CoverCache.CoverKind.FULL, cover.bytes).getOrNull()?.absolutePath

    /** Уже сохранённая обложка книги: путь из базы, если файл на месте. */
    private fun cachedCoverOf(book: BookEntity): String? =
        book.coverPath?.takeIf { path -> File(path).isFile }

    /** Собирает [BookInput] поверх `content://`-URI книги. */
    private fun BookEntity.inputOf(): BookInput = BookInput(
        fileName = fileName,
        extension = extension,
        mimeType = mimeType.ifEmpty { null },
        sizeBytes = sizeBytes,
        openStream = { contentOpener.openStream(documentUri) ?: error("Провайдер не отдал поток") },
        openSeekable = { openChannel() },
        bytes = { offset, length -> readRange(offset, length) },
    )

    private fun BookEntity.openChannel(): FileChannel? = contentOpener.openSeekableChannel(documentUri)

    private fun BookEntity.readRange(offset: Long, length: Int): ByteArray =
        contentOpener.openStream(documentUri)?.use { stream ->
            // `readNBytes` здесь нельзя: он появился только в API 33 при `minSdk 24`, и на старых
            // устройствах разбор метаданных падал бы с NoSuchMethodError (нашёл lint, режим NewApi).
            stream.readRange(offset, length)
        } ?: ByteArray(0)

    /**
     * Успешный разбор.
     *
     * Пустое поле в метаданных не затирает уже известное значение: у TXT-файла, например,
     * авторов нет вовсе, и запись «без автора» была бы потерей информации для книг, чьё имя
     * файла уже разобрано.
     */
    private fun BookEntity.applyParsed(
        metadata: BookMetadata,
        coverPath: String?,
        now: Long,
    ): BookEntity {
        val authors = metadata.authors.joinToString(AUTHORS_SEPARATOR)
        val sortTitle = metadata.sortTitleOf()
        return copy(
            title = metadata.title.ifEmpty { title },
            sortTitle = sortTitle.ifEmpty { this.sortTitle },
            authorsText = authors.ifEmpty { authorsText },
            seriesName = metadata.seriesName ?: seriesName,
            seriesIndex = metadata.seriesIndex ?: seriesIndex,
            language = metadata.language ?: language,
            publishedYear = metadata.publishedYear ?: publishedYear,
            publisher = metadata.publisher ?: publisher,
            description = metadata.description ?: description,
            pageCount = metadata.pageCount ?: pageCount,
            coverPath = coverPath ?: this.coverPath,
            coverUpdatedAt = if (coverPath != null) now else coverUpdatedAt,
            metadataSource = MetadataSource.EMBEDDED,
            parseState = ParseState.OK,
            parseError = null,
            updatedAt = now,
        )
    }

    private fun BookEntity.applyFailed(reason: String, now: Long): BookEntity =
        copy(parseState = ParseState.FAILED, parseError = reason.take(ERROR_MAX), updatedAt = now)

    companion object {
        const val KEY_BOOK_ID: String = "bookId"

        /** Общий тег для экрана прогресса разбора. */
        const val WORK_TAG: String = "metadata"

        /** Входной флаг: разобрать книгу заново, даже если она уже разобрана. */
        const val KEY_FORCE: String = "force"

        /** Входной флаг: заново извлечь обложку, даже если она уже в кэше. */
        const val KEY_REFRESH_COVER: String = "refreshCover"

        private const val AUTHORS_SEPARATOR = ", "
        private const val NO_PARSER = "Формат не поддерживается"
        private const val ERROR_MAX = 300

        /**
         * Признак формата по содержимому не читается заранее: `ParserRegistry.select` сам решает
         * по расширению и MIME, а содержимое проверяет, только если расширение подвело.
         */
        private val EMPTY_PREFIX: ByteArray = ByteArray(0)
    }
}

/**
 * Каким путём записать книге обложку после разбора.
 *
 * Обложка кэшируется: файл книги лежит на устройстве и не меняется, поэтому перечитывать его
 * ради картинки при каждом разборе метаданных бессмысленно — разбор нужен ради названия,
 * авторов и жанров. Заново картинка берётся в двух случаях: явная команда «Обновить из файла»
 * ([refreshCover]) и изменение самого файла (тогда скан сбрасывает кэш и [cachedCover] уже пуст).
 *
 * Если разбор обложку не дал, а сохранённая есть — возвращается сохранённая: `null` означал бы
 * «стереть обложку», и частичный разбор терял бы картинку.
 *
 * @param store запись новой обложки в кэш; вызывается только когда это действительно нужно.
 */
internal suspend fun chooseCoverPath(
    cachedCover: String?,
    hasExtractedCover: Boolean,
    refreshCover: Boolean,
    store: suspend () -> String?,
): String? = when {
    !refreshCover && cachedCover != null -> cachedCover
    hasExtractedCover -> store() ?: cachedCover
    else -> cachedCover
}
