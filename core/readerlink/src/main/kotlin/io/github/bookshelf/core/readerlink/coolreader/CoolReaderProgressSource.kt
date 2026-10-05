package io.github.bookshelf.core.readerlink.coolreader

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressDatabase
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import io.github.bookshelf.core.readerlink.progress.readerPathMatches
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Прогресс книги из базы CoolReader.
 *
 * CoolReader держит всё своё состояние в SQLite на общей памяти: `/sdcard/.cr3/cr3db.sqlite`
 * (поле «CRDBService.getDatabaseDir»: `Environment.getExternalStorageDirectory() + "/.cr3"`).
 * Последняя позиция книги — строка `bookmark` с `type = 0` (`Bookmark.TYPE_LAST_POSITION`), а
 * `percent` в ней — это **1/100 процента** (в crengine `lvdocview.h`: «returns position in 1/100 of
 * percents (0..10000)»), поэтому 1874 — это 18,74 %. Проверено на живой базе устройства 05.10.2026.
 *
 * Базу читаем **один раз за проход**: `connect()` открывает копию, забирает последние позиции всех
 * книг и закрывает её (копии убираются), а `progressFor` отвечает из памяти. Перебирать базу на
 * каждую книгу значило бы копировать файл десятки раз за одну синхронизацию.
 *
 * Доступа ко всей памяти нет (flavor `play` или отозванное разрешение) — `connect()` вернёт
 * `false`, и прогресс не появится; нулём процентов это не подменяется.
 */
@Singleton
class CoolReaderProgressSource @Inject constructor(
    private val databases: ProgressDatabase,
) : ReadingProgressSource {

    override val id: ProgressSourceId = ProgressSourceId.COOLREADER_DB

    /** CoolReader наружу «что открыто» не отдаёт: его читают по конкретной книге. */
    override val knowsOpenBook: Boolean = false

    /** Последние позиции книг, прочитанные при `connect()`; ключ — имя файла. */
    private var positions: Map<String, List<CoolReaderPosition>> = emptyMap()

    override suspend fun connect(): Boolean {
        val query = databases.open(DATABASE_PATH) ?: return false
        return try {
            positions = query.rows(POSITIONS_SQL, listOf(TYPE_LAST_POSITION))
                .mapNotNull { it.toPosition() }
                .groupBy { it.fileName }
            true
        } finally {
            query.close()
        }
    }

    override suspend fun disconnect() {
        positions = emptyMap()
    }

    override suspend fun openedBook(): ReaderBookAddress? = null

    override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
        val position = match(book)
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения CoolReader",
                    message = "в базе CoolReader нет позиции книги ${book.fileName}",
                ),
            )
        return AppResult.success(
            ReadingProgressSnapshot(
                percent = position.percent,
                pageIndex = null,
                locatorJson = position.locator,
                // У CoolReader время записи есть (`bookmark.time_stamp`) — по нему и решается, чья
                // запись свежее, когда книгу читали ещё и в другой читалке.
                updatedAt = position.timeStamp.takeIf { it > 0L },
            ),
        )
    }

    /**
     * Найти позицию книги среди прочитанных.
     *
     * В базе CoolReader лежит **реальный путь** (`/sdcard/Books/…`), а у нас адрес SAF: путь читалки
     * должен совпасть с относительным путём книги и с папкой библиотеки (`readerPathMatches`).
     * По одному имени файла не угадываем: у двух книг с одинаковым именем процент попадёт не той, а
     * это хуже, чем не записать ничего.
     */
    private fun match(book: ProgressBookRef): CoolReaderPosition? {
        val candidates = positions[book.fileName].orEmpty()
        if (candidates.isEmpty()) return null
        // Берём только те записи, чей путь действительно наш (см. `readerPathMatches`): если ни одна
        // не подошла, книгу в CoolReader не открывали, и свежая по времени запись здесь не поможет.
        return candidates
            .filter { readerPathMatches(it.pathname, book) }
            .maxByOrNull { it.timeStamp }
    }

    /** Строка базы в позицию; `null` — строку разобрать нельзя (колонки нет или значение мусорное). */
    private fun Map<String, String?>.toPosition(): CoolReaderPosition? {
        val pathname = this["pathname"] ?: return null
        val percent = this["percent"]?.toIntOrNull() ?: return null
        return CoolReaderPosition(
            pathname = pathname,
            fileName = this["filename"] ?: pathname.substringAfterLast('/'),
            percent = (percent / PERCENT_DIVISOR).coerceIn(0f, MAX_PERCENT),
            locator = this["start_pos"],
            timeStamp = this["time_stamp"]?.toLongOrNull() ?: 0L,
        )
    }

    private data class CoolReaderPosition(
        val pathname: String,
        val fileName: String,
        val percent: Float,
        val locator: String?,
        val timeStamp: Long,
    )

    private companion object {

        /**
         * База CoolReader на общей памяти.
         *
         * Если писать в `.cr3` нельзя, CoolReader уходит в каталог данных приложения — оттуда
         * прогресс не достать ничем, кроме root, и это ограничение читалки, а не наше.
         */
        const val DATABASE_PATH = "/sdcard/.cr3/cr3db.sqlite"

        /** `Bookmark.TYPE_LAST_POSITION` в crengine равен нулю. */
        const val TYPE_LAST_POSITION = "0"

        const val POSITIONS_SQL =
            "select b.pathname as pathname, b.filename as filename, k.percent as percent, " +
                "k.start_pos as start_pos, k.time_stamp as time_stamp " +
                "from bookmark k join book b on b.id = k.book_fk where k.type = ?"

        /** `percent` в базе — 1/100 процента. */
        const val PERCENT_DIVISOR = 100f

        const val MAX_PERCENT = 100f
    }
}
