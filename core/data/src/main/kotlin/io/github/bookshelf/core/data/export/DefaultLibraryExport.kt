package io.github.bookshelf.core.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.author.parseAuthors
import io.github.bookshelf.core.data.mapper.toDomain
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.database.BookshelfDatabase
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Выгрузка библиотеки: собрать снимок из базы и записать его в выбранный документ.
 *
 * Решения, которые здесь зафиксированы и которые нельзя менять по мелочи:
 * * **авторы — из связей.** Строка `authorsText` остаётся запасным путём только для книг, до
 *   которых разбор метаданных ещё не дошёл: после задачи «авторы как связи» правильный ответ это
 *   `book_authors`, и выгрузка обязана совпадать с тем, что видно на экране;
 * * **жанры — кодами** (`sf_space`), а не подписями: подпись переводится `genreDisplayName`,
 *   и в файле должно лежать то же значение, что в фильтрах и правилах умных полок;
 * * **порядок книг — по названию** (без учёта регистра): строки базы порядка не имеют, а две
 *   выгрузки подряд должны отличаться только данными, а не перестановкой;
 * * **число полок и книг в файле — по правилам умной полки**, поэтому счётчики берутся у
 *   [CollectionRepository], а не считаются здесь заново.
 */
@Singleton
class DefaultLibraryExport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: BookshelfDatabase,
    private val collections: CollectionRepository,
    private val clock: AppClock,
) : LibraryExportRepository {

    override suspend fun snapshot(): LibraryExportContent {
        val books = database.bookDao().getAll()
        val authors = database.authorDao().getAllBookAuthors()
            .groupBy({ row -> row.bookId }, { row -> row.name })
        val genres = database.tagDao().getAllBookTags()
            .groupBy({ row -> row.bookId }, { row -> row.name })
        val progress = database.readingProgressDao().getAll()
            .associate { row -> row.bookId to row.percent }

        return LibraryExportContent(
            books = books
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { entity -> entity.title })
                .map { entity ->
                    entity.toDomain(
                        authors = authors[entity.id] ?: parseAuthors(entity.authorsText),
                    ).copy(progressPercent = progress[entity.id])
                },
            collections = collections.observeCollections().first(),
            genres = genres,
        )
    }

    override suspend fun exportTo(destination: Uri): AppResult<LibraryExportSummary> = appRunCatching {
        val content = snapshot()
        val json = exportLibrary(
            exportedAt = clock.nowMillis(),
            books = content.books,
            collections = content.collections,
            genres = content.genres,
        )
        val bytes = json.toByteArray(Charsets.UTF_8)
        // Режим `wt` — «писать с начала»: повторная выгрузка в тот же документ не должна
        // дописывать новый JSON к старому, иначе файл перестал бы разбираться.
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Не удалось открыть выбранный файл для записи")
        output.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
        LibraryExportSummary(books = content.books.size, collections = content.collections.size)
    }
}
