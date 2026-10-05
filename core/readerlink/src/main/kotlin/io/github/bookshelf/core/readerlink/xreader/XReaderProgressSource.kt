package io.github.bookshelf.core.readerlink.xreader

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ProgressStorage
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import io.github.bookshelf.core.readerlink.progress.readerPathMatches
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Прогресс книги из кэша XReader.
 *
 * XReader (`com.xrz.xreaderV3`) держит состояние книг в `/sdcard/XRZBookCache/V3/js<идентификатор>`:
 * gzip, внутри `bookStateInfo` — вложенная строка JSON с путём книги, текущей и общей страницей.
 * Проверено на файле устройства 05.10.2026.
 *
 * Что здесь неочевидно:
 * * файлы читаются **байтами**: это gzip, текстом его не прочитать (порт отдаёт и `readBytes`);
 * * каталог перебирается целиком за один проход, а не по файлу на книгу: имя файла — случайный
 *   идентификатор, и найти состояние по книге иначе нельзя;
 * * при нескольких состояниях одной книги берётся то, которое закончили читать позже (`bookEndTime`),
 *   а не с большей страницей: страница может быть больше у старой копии книги;
 * * копии, которые XReader делает сам (`/sdcard/xReader/thirdparty/…`) при запуске по SAF, по имени
 *   **не угадываются**: на устройстве таких копий нет (XReader запускается по реальному пути), а
 *   одноимённая запись может принадлежать другой книге.
 *
 * Доступа ко всей памяти нет (flavor `play`) — `connect()` вернёт `false`, и прогресс не появится.
 */
@Singleton
class XReaderProgressSource @Inject constructor(
    private val files: ProgressStorage,
) : ReadingProgressSource {

    override val id: ProgressSourceId = ProgressSourceId.XREADER_CACHE

    /** XReader наружу «что открыто» не отдаёт: его читают по конкретной книге. */
    override val knowsOpenBook: Boolean = false

    /** Состояния книг, прочитанные при `connect()`. */
    private var states: List<XReaderJson.State> = emptyList()

    override suspend fun connect(): Boolean {
        val names = files.children(CACHE_DIRECTORY)
        // Каталога нет — считаем источник недоступным: это и случай «читалки нет», и «нет доступа ко
        // всей памяти» во flavor play.
        if (names.isEmpty()) return false
        states = names.filter { it.startsWith(FILE_PREFIX) }
            .mapNotNull { name -> files.readBytes("$CACHE_DIRECTORY/$name")?.let(XReaderJson::parse) }
        return true
    }

    override suspend fun disconnect() {
        states = emptyList()
    }

    override suspend fun openedBook(): ReaderBookAddress? = null

    override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
        val state = states
            .filter { readerPathMatches(it.bookPath, book) }
            .maxByOrNull { it.endTime.orEmpty() }
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения XReader",
                    message = "в кэше XReader нет состояния книги ${book.fileName}",
                ),
            )
        val percent = state.percent
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения XReader",
                    message = "XReader ещё не разобрал книгу ${book.fileName}: страниц нет",
                ),
            )
        return AppResult.success(
            ReadingProgressSnapshot(
                percent = percent,
                pageIndex = state.currentPage,
                locatorJson = null,
                // `bookEndTime` — когда книгу закрыли в XReader; по нему и сравниваются источники.
                updatedAt = state.updatedAt,
            ),
        )
    }

    private companion object {

        /** Каталог кэша XReader на общей памяти. */
        const val CACHE_DIRECTORY = "/sdcard/XRZBookCache/V3"

        /** Имена файлов состояния начинаются с `js`. */
        const val FILE_PREFIX = "js"
    }
}
