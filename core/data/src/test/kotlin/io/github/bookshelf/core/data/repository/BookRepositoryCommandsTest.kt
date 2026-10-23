package io.github.bookshelf.core.data.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.database.model.OpdsDownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Команды над книгой: избранное, оценка, статус чтения, метаданные и удаление.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookRepositoryCommandsTest : BookRepositoryTestBase() {
    @Test
    fun set_rating_rejects_a_value_outside_the_scale() = runBlocking {
        givenLibrary()

        assertThat(repository.setRating("war", 7).isFailure).isTrue()
        assertThat(repository.setRating("war", -1).isFailure).isTrue()
    }

    @Test
    fun set_rating_accepts_the_scale_boundaries() = runBlocking {
        givenLibrary()

        repository.setRating("war", 5).assertOk()
        assertThat(repository.getById("war").getOrNull()?.rating).isEqualTo(5)

        repository.setRating("war", null).assertOk()
        assertThat(repository.getById("war").getOrNull()?.rating).isNull()
    }

    @Test
    fun mark_read_changes_status_without_touching_metadata() = runBlocking {
        givenLibrary()
        val before = repository.getById("war").getOrNull()

        repository.markRead("war", ReadingStatus.READING).assertOk()

        val after = repository.getById("war").getOrNull()
        assertThat(after?.readingStatus).isEqualTo(ReadingStatus.READING)
        assertThat(after?.title).isEqualTo(before?.title)
    }

    @Test
    fun update_metadata_writes_into_books_and_keeps_the_override_as_protection() = runBlocking {
        givenLibrary()

        repository.updateMetadata("war", MetadataPatch(title = "Война и мир (ред.)")).assertOk()

        assertThat(repository.getById("war").getOrNull()?.title).isEqualTo("Война и мир (ред.)")
        // Правка пишется и в строку books: её читают полка, обзор серий, фасеты и поиск, а не
        // только карточка (решение Александра, 29.09.2026 — правка должна переживать перенос файла).
        assertThat(database.bookDao().getById("war")?.title).isEqualTo("Война и мир (ред.)")
        // Строка в metadata_overrides остаётся: она возвращает правку, если скан перезапишет books.
        assertThat(database.metadataOverrideDao().getByBookId("war")?.title)
            .isEqualTo("Война и мир (ред.)")
    }

    @Test
    fun update_metadata_writes_series_and_description_into_books() = runBlocking<Unit> {
        givenLibrary()

        repository.updateMetadata(
            "war",
            MetadataPatch(seriesName = "Хроники", seriesIndex = 2.0, description = "Правка пользователя"),
        ).assertOk()

        val row = database.bookDao().getById("war")
        assertThat(row?.seriesName).isEqualTo("Хроники")
        assertThat(row?.seriesIndex).isEqualTo(2.0)
        assertThat(row?.description).isEqualTo("Правка пользователя")
        // Поля, которых пользователь не касался, остаются как в файле.
        assertThat(row?.title).isEqualTo("Война и мир")
        assertThat(row?.authorsText).isEqualTo("Лев Толстой")
        // Обзор серий и фильтр читают books: новая серия обязана быть видна и там.
        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(series = setOf("Хроники"))),
        ).first()
        assertThat(page.items.map { it.id }).containsExactly("war")
    }

    @Test
    fun delete_permanently_removes_the_book_with_its_links() = runBlocking<Unit> {
        givenLibrary()
        // Связи, которые в схеме не защищены внешним ключом: именно они оставались сиротами.
        database.authorDao().insertBookAuthors(
            listOf(BookAuthorCrossRef(bookId = "war", authorId = "author:leo")),
        )
        database.tagDao().insertBookTags(
            listOf(BookTagCrossRef(bookId = "war", tagId = "tag:classic")),
        )
        database.collectionDao().insertBooks(
            listOf(CollectionBookCrossRef(collectionId = "shelf-classic", bookId = "war")),
        )

        val deleted = repository.deletePermanently(listOf("war")).getOrNull()

        assertThat(deleted?.deleted).isEqualTo(1)
        assertThat(database.bookDao().getById("war")).isNull()
        assertThat(database.metadataOverrideDao().getByBookId("war")).isNull()
        // Связи не должны оставаться сиротами: на устройстве нашлись 19 строк `book_tags`
        // от 9 удалённых книг, потому что удалялась только строка `books`.
        assertThat(countRows("book_tags", "war")).isEqualTo(0)
        assertThat(countRows("book_authors", "war")).isEqualTo(0)
        assertThat(countRows("collection_books", "war")).isEqualTo(0)
    }

    @Test
    fun delete_permanently_clears_the_download_link_to_the_book() = runBlocking<Unit> {
        givenLibrary()
        database.opdsDownloadDao().upsert(
            OpdsDownloadEntity(
                id = DOWNLOAD_ID,
                catalogId = "catalog:one",
                entryId = "entry:one",
                title = "Война и мир",
                downloadUrl = "https://example.org/war.fb2",
                targetSourceId = "source:one",
                targetRelativeDir = "",
                state = OpdsDownloadState.DONE,
                bookId = "war",
                createdAt = 1_000L,
            ),
        )

        repository.deletePermanently(listOf("war")).getOrNull()

        // История загрузок остаётся, а ссылка на удалённую книгу — нет: по ней экран загрузок
        // рисовал «открыть скачанную книгу» и открывал карточку книги, которой уже нет.
        assertThat(database.opdsDownloadDao().getById(DOWNLOAD_ID)?.bookId).isNull()
    }

    @Test
    fun copies_are_listed_with_the_title_of_their_book() = runBlocking<Unit> {
        givenLibrary()
        givenCopy(bookId = "war", copyUri = COPY_URI)

        val copies = repository.copies().first()

        assertThat(copies.map { copy -> copy.copyDocumentUri }).containsExactly(COPY_URI)
        assertThat(copies.first().bookTitle).isEqualTo("Война и мир")
        assertThat(copies.first().sizeBytes).isEqualTo(100L)
    }

    @Test
    fun deleting_a_copy_removes_the_file_and_its_link() = runBlocking<Unit> {
        givenLibrary()
        givenCopy(bookId = "war", copyUri = COPY_URI)

        val result = repository.removeCopies(listOf(COPY_URI)).getOrNull()

        assertThat(result?.deleted).isEqualTo(1)
        assertThat(files.deleted).contains(COPY_URI)
        assertThat(database.bookCopyDao().findByUri(COPY_URI)).isNull()
    }

    @Test
    fun a_refused_copy_keeps_its_link_so_it_can_be_retried() = runBlocking<Unit> {
        givenLibrary()
        givenCopy(bookId = "war", copyUri = COPY_URI)
        files.refuse = AppError.PermissionDenied(message = "нет доступа к папке")

        val result = repository.removeCopies(listOf(COPY_URI)).getOrNull()

        assertThat(result?.failed).isEqualTo(1)
        // Связь остаётся: файл на диске, и в списке копий он должен быть виден для повтора.
        assertThat(database.bookCopyDao().findByUri(COPY_URI)).isNotNull()
    }

    @Test
    fun delete_permanently_removes_copies_of_the_book_from_the_device() = runBlocking<Unit> {
        givenLibrary()
        // Копия, созданная читалкой внутри библиотеки: тот же файл под другим адресом.
        givenCopy(bookId = "war", copyUri = COPY_URI)

        val deleted = repository.deletePermanently(listOf("war")).getOrNull()

        assertThat(deleted?.deleted).isEqualTo(1)
        assertThat(files.deleted).contains(COPY_URI)
        assertThat(database.bookCopyDao().findByUri(COPY_URI)).isNull()
    }

    @Test
    fun sorting_by_last_opened_puts_recent_books_first_and_never_opened_last() = runBlocking<Unit> {
        givenLibrary()
        givenOpenedAt("war", 1_000L)
        givenOpenedAt("anna", 5_000L)

        val descending = repository.observePage(
            LibraryQuery(sort = SortOrder.LAST_OPENED, descending = true),
        ).first()
        val ascending = repository.observePage(
            LibraryQuery(sort = SortOrder.LAST_OPENED, descending = false),
        ).first()

        // Свежие сверху при убывании и в обратном порядке при возрастании...
        assertThat(descending.items.take(2).map { it.id }).containsExactly("anna", "war").inOrder()
        assertThat(ascending.items.take(2).map { it.id }).containsExactly("war", "anna").inOrder()
        // ...но книги, которые не открывали, не первые ни при каком направлении (дефект 01.10.2026).
        assertThat(descending.items.last().lastOpenedAt).isNull()
        assertThat(ascending.items.last().lastOpenedAt).isNull()
    }

    @Test
    fun sorting_by_progress_puts_read_books_first_and_unread_last() = runBlocking<Unit> {
        givenLibrary()
        givenProgress("war", 0.5f)
        givenProgress("anna", 0.2f)

        val descending = repository.observePage(
            LibraryQuery(sort = SortOrder.PROGRESS, descending = true),
        ).first()
        val ascending = repository.observePage(
            LibraryQuery(sort = SortOrder.PROGRESS, descending = false),
        ).first()

        // «По прогрессу» открывается книгами, которые читают: убывание — самые прочитанные, а
        // возрастание — те, что читать начали и продвинулись меньше.
        assertThat(descending.items.take(2).map { it.id }).containsExactly("war", "anna").inOrder()
        assertThat(ascending.items.take(2).map { it.id }).containsExactly("anna", "war").inOrder()
        // Книги без прогресса — в конце в обоих направлениях.
        assertThat(descending.items.drop(2).map { it.progressPercent }).containsExactly(null, null, null)
        assertThat(ascending.items.drop(2).map { it.progressPercent }).containsExactly(null, null, null)
    }

    @Test
    fun update_metadata_with_empty_values_clears_them_everywhere() = runBlocking<Unit> {
        givenLibrary()

        // Пустая строка — это «убрать значение»: пользователь стёр серию и описание в форме.
        repository.updateMetadata(
            "war",
            MetadataPatch(seriesName = "", description = ""),
        ).assertOk()

        val row = database.bookDao().getById("war")
        assertThat(row?.seriesName).isNull()
        assertThat(row?.description).isNull()
        // В правке остаётся пустая строка, а не пустое место: иначе скан вернул бы серию из файла.
        val override = database.metadataOverrideDao().getByBookId("war")
        assertThat(override?.seriesName).isEqualTo("")
        assertThat(override?.description).isEqualTo("")
        // Карточка тоже показывает, что серии больше нет.
        assertThat(repository.getById("war").getOrNull()?.seriesName).isNull()
        // Полка по серии книгу больше не находит.
        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(series = setOf("Классика"))),
        ).first()
        assertThat(page.items.map { it.id }).doesNotContain("war")
    }

    @Test
    fun update_metadata_can_clear_only_the_series_index() = runBlocking<Unit> {
        givenLibrary()
        repository.updateMetadata("war", MetadataPatch(seriesIndex = 4.0)).assertOk()

        // Номер убираем, название серии оставляем.
        repository.updateMetadata("war", MetadataPatch(clearSeriesIndex = true)).assertOk()

        val row = database.bookDao().getById("war")
        assertThat(row?.seriesIndex).isNull()
        assertThat(row?.seriesName).isEqualTo("Классика")
        // Правка не возвращает старый номер на экран: в ней он тоже очищен.
        assertThat(database.metadataOverrideDao().getByBookId("war")?.seriesIndex).isNull()
        assertThat(repository.getById("war").getOrNull()?.seriesIndex).isNull()
    }

    @Test
    fun update_metadata_rewrites_authors_in_books_and_in_the_links() = runBlocking<Unit> {
        givenLibrary()

        repository.updateMetadata("war", MetadataPatch(authorsText = "Лев Толстой, Переводчиков"))
            .assertOk()

        assertThat(database.bookDao().getById("war")?.authorsText).isEqualTo("Лев Толстой, Переводчиков")
        // Авторское измерение читает связи: фильтр по новому автору обязан находить книгу.
        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf("Переводчиков"))),
        ).first()
        assertThat(page.items.map { it.id }).containsExactly("war")
    }

    @Test
    fun update_metadata_recomputes_sort_title_and_is_found_by_search() = runBlocking {
        givenLibrary()

        repository.updateMetadata("war", MetadataPatch(title = "The War and Peace")).assertOk()

        assertThat(database.bookDao().getById("war")?.sortTitle).isEqualTo("War and peace")
        // FTS синхронизируется триггерами Room: правка названия обязана быть видна поиску.
        val found = database.searchDao().search("War", 10).first()
        assertThat(found.map { it.id }).contains("war")
    }

    @Test
    fun update_metadata_of_a_missing_book_reports_not_found() = runBlocking {
        val result = repository.updateMetadata("нет-такой", MetadataPatch(title = "X"))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun delete_removes_only_the_record() = runBlocking {
        givenLibrary()

        repository.delete("war").assertOk()

        assertThat(repository.getById("war").getOrNull()).isNull()
        assertThat(repository.observePage(LibraryQuery()).first().totalCount).isEqualTo(4)
    }

    @Test
    fun `books inside one series are ordered by series index then year`() = runBlocking {
        givenLibrary()
        database.bookDao().upsertAll(
            listOf(
                // Заведомо перемешаны: проверяется порядок чтения серии, а не порядок вставки.
                book("chronicle-3", "Третья", "Лев Толстой", series = "Хроники", year = 2003, ext = "fb2")
                    .copy(seriesIndex = 3.0),
                book("chronicle-1", "Первая", "Лев Толстой", series = "Хроники", year = 2001, ext = "fb2")
                    .copy(seriesIndex = 1.0),
                book("chronicle-2", "Вторая", "Лев Толстой", series = "Хроники", year = 2002, ext = "fb2")
                    .copy(seriesIndex = 2.0),
                // Без номера в серии: уходит в конец, внутри «без номера» — по году издания.
                book("chronicle-late", "Поздняя", "Лев Толстой", series = "Хроники", year = 2010, ext = "fb2"),
            ),
        )

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(series = setOf("Хроники")), pageSize = 20),
        ).first()

        // Порядок выпуска важнее выбранной сортировки: серия «1, 2, 3, затем без номера».
        assertThat(page.items.map { book -> book.id })
            .containsExactly("chronicle-1", "chronicle-2", "chronicle-3", "chronicle-late")
            .inOrder()
    }

    @Test
    fun `rescanAll keeps the shelf and scans enabled sources`() = runBlocking<Unit> {
        givenLibrary()
        val before = database.bookDao().getAll().size
        sources.sources = listOf(source(id = "s1"), source(id = "s2", enabled = false))

        repository.rescanAll().assertOk()

        // Книги остаются на месте: скан сам удаляет исчезнувшее и переразбирает изменённое.
        // Раньше команда сначала очищала таблицу книг, и сбойный прогон оставлял человека
        // без полки — именно это случилось 30.09.2026 (79 книг исчезли из базы).
        assertThat(database.bookDao().getAll()).hasSize(before)
        // Фейк помнит, какие источники просили сканировать: выключенный не должен попасть в список.
        assertThat(scans.started).containsExactly("s1")
        // И просили именно заменить стоящую работу: иначе запрос человека терялся бы, пока
        // сбойный прогон ждёт повтора (политика KEEP).
        assertThat(scans.restarted).containsExactly("s1")
    }
}
