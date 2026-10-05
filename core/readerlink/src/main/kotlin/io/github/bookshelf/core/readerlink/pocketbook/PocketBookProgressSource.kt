package io.github.bookshelf.core.readerlink.pocketbook

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
 * Прогресс книги из базы PocketBook.
 *
 * PocketBook держит библиотеку в SQLite на общей памяти: `/sdcard/.pocketbook/books.db` (рядом
 * `-wal`/`-shm` — журнал активен, поэтому базу читаем только копией вместе с журналами). Прогресс
 * хранится **тегами** книги, а не колонками: `doc.read_progress` — доля 0..1 строкой,
 * `doc.last_read_position` — позиция (`pbr:/img?page=3&offs=1#epubcfi(…)`). Проверено на живой базе
 * устройства 05.10.2026.
 *
 * Книга опознаётся по имени файла, а при нескольких одинаковых именах — по хвосту относительного
 * пути: в базе путь и имя лежат отдельно (`Paths.Path` — папка, `Files.Name` — файл), причём один и
 * тот же файл бывает записан дважды (`/sdcard/…` и `/storage/emulated/0/…`). Строки сводим по книге
 * (`Items.OID`), иначе «свежесть» определялась бы по случайной из двух записей.
 *
 * Доступа ко всей памяти нет (flavor `play`, отозванное разрешение) — `connect()` вернёт `false`, и
 * прогресс не появится; нулём процентов это не подменяется.
 */
@Singleton
class PocketBookProgressSource @Inject constructor(
    private val databases: ProgressDatabase,
) : ReadingProgressSource {

    override val id: ProgressSourceId = ProgressSourceId.POCKETBOOK_DB

    /** PocketBook наружу «что открыто» не отдаёт: его читают по конкретной книге. */
    override val knowsOpenBook: Boolean = false

    /** Книги с прогрессом, прочитанные при `connect()`; ключ — имя файла. */
    private var entries: Map<String, List<Entry>> = emptyMap()

    override suspend fun connect(): Boolean {
        val query = databases.open(DATABASE_PATH) ?: return false
        return try {
            entries = query.rows(TAGS_SQL, listOf(READ_PROGRESS_TAG, LAST_POSITION_TAG))
                .toEntries()
                .filter { it.share != null }
                .groupBy { it.name }
            true
        } finally {
            query.close()
        }
    }

    override suspend fun disconnect() {
        entries = emptyMap()
    }

    override suspend fun openedBook(): ReaderBookAddress? = null

    override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
        val entry = match(book)
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения PocketBook",
                    message = "в базе PocketBook нет доли прочитанного для ${book.fileName}",
                ),
            )
        return AppResult.success(
            ReadingProgressSnapshot(
                percent = entry.percent,
                pageIndex = null,
                locatorJson = entry.position,
                // `Tags.TimeEdt` — когда запись сделана; этим и сравниваются источники.
                updatedAt = entry.timeEdt.takeIf { it > 0L },
            ),
        )
    }

    /**
     * Найти книгу с прогрессом.
     *
     * Папка из базы плюс имя файла должны сойтись с нашим относительным путём и папкой библиотеки
     * (`readerPathMatches`). По одному имени не угадываем: у двух книг с одинаковым именем процент
     * попадёт не той, а это хуже, чем не записать ничего. Если путь не сошёлся (книгу переложили),
     * остаётся вторая примета PocketBook — размер файла.
     */
    private fun match(book: ProgressBookRef): Entry? {
        val candidates = entries[book.fileName].orEmpty()
        if (candidates.isEmpty()) return null
        // Папка и имя файла в базе лежат отдельно, вместе они дают путь, который и сверяем с нашим.
        val byPath = candidates
            .filter { readerPathMatches("${it.folder.orEmpty()}${it.name}", book) }
            .maxByOrNull { it.timeEdt }
        if (byPath != null) return byPath
        // Путь не сошёлся — книгу могли переложить внутри библиотеки. У PocketBook есть вторая
        // примета, которой нет у других читалок: размер файла (`Files.Len`). Берём её, только если
        // совпал ровно один: два одинаковых размера ничего не доказывают, а чужой процент на полке
        // хуже отсутствия процента.
        if (book.sizeBytes <= 0L) return null
        return candidates.filter { it.sizeBytes == book.sizeBytes }.singleOrNull()
    }

    /**
     * Свести строки тегов в записи по книгам.
     *
     * Теги приходят отдельными строками (`doc.read_progress` и `doc.last_read_position` — разные
     * строки одной книги), а обложек и прочих тегов мы не спрашиваем вовсе.
     */
    private fun List<Map<String, String?>>.toEntries(): List<Entry> =
        groupBy { it["item_id"].orEmpty() }
            .mapNotNull { (itemId, rows) -> rows.toEntry(itemId) }

    private fun List<Map<String, String?>>.toEntry(itemId: String): Entry? {
        if (itemId.isEmpty()) return null
        val name = firstNotNullOfOrNull { it["name"] } ?: return null
        val share = firstNotNullOfOrNull { row -> row.takeIf { it["tag"] == READ_PROGRESS_TAG }?.get("val") }
        return Entry(
            itemId = itemId,
            name = name,
            folder = firstNotNullOfOrNull { it["path"] },
            title = firstNotNullOfOrNull { it["title"] },
            sizeBytes = firstNotNullOfOrNull { it["len"]?.toLongOrNull() },
            share = share?.toFloatOrNull(),
            position = firstNotNullOfOrNull { row -> row.takeIf { it["tag"] == LAST_POSITION_TAG }?.get("val") },
            timeEdt = mapNotNull { it["time_edt"]?.toLongOrNull() }.maxOrNull() ?: 0L,
        )
    }

    /** Книга с прогрессом: то, что из базы нужно для снимка. */
    private data class Entry(
        val itemId: String,
        val name: String,
        val folder: String?,
        val title: String?,
        /** Размер файла из `Files.Len`; `null` — размера в базе нет (старые записи). */
        val sizeBytes: Long?,
        val share: Float?,
        val position: String?,
        val timeEdt: Long,
    ) {
        /** Доля 0..1 превращается в проценты и ограничивается книгой: «150 %» на полке нет. */
        val percent: Float get() = ((share ?: 0f) * PERCENT).coerceIn(0f, PERCENT)
    }

    private companion object {

        /**
         * База PocketBook на общей памяти.
         *
         * Скрытая папка: через SAF не читается, нужен доступ ко всей памяти. У PocketBook есть и
         * облако со своим аккаунтом, но в интеграцию мы его не берём.
         */
        const val DATABASE_PATH = "/sdcard/.pocketbook/books.db"

        const val READ_PROGRESS_TAG = "doc.read_progress"
        const val LAST_POSITION_TAG = "doc.last_read_position"

        const val TAGS_SQL =
            "select t.ItemID as item_id, tn.TagName as tag, t.Val as val, f.Name as name, " +
                "p.Path as path, b.Title as title, t.TimeEdt as time_edt, f.Len as len " +
                "from Tags t " +
                "join TagNames tn on tn.OID = t.TagID " +
                "left join Files f on f.BookID = t.ItemID " +
                "left join Paths p on p.OID = f.PathID " +
                "left join Books b on b.OID = t.ItemID " +
                "where tn.TagName in (?, ?)"

        const val PERCENT = 100f
    }
}
