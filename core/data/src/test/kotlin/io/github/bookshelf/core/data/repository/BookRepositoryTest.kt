package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.fb2opt.DefaultFb2Optimizer
import io.github.bookshelf.core.fb2opt.Fb2ImageOptimizer
import io.github.bookshelf.core.fb2opt.JpegEntropyOptimizer
import io.github.bookshelf.core.testing.fake.FakeBookContentOpener
import io.github.bookshelf.core.storage.files.ContentHasher
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.database.model.OpdsDownloadState
import io.github.bookshelf.core.database.entity.BookCopyEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.github.bookshelf.core.testing.fake.FakeScanRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import io.github.bookshelf.core.testing.fake.FakeReaderSession

/**
 * Чтение полки и команды над книгами (docs/architecture.md §3.1, §3.5).
 *
 * Проверяется ровно то, без чего экран полки работать не будет: фильтры должны сочетаться,
 * сортировка — применяться, страница — считаться по тем же фильтрам, а команды — менять состояние
 * книги, не трогая файл.
 *
 * Два акцента на том, где ошибка не видна глазом, но ломает полку:
 * * пагинация обязана быть **стабильной** — без добивочного ключа сортировки по `id` строки с
 *   одинаковым заголовком прыгают между страницами, и пользователь видит дубли и пропуски;
 * * `totalCount` обязан считаться по **тем же** фильтрам, что и выборка, — иначе подпись
 *   «Стр. 1/12» показывает число, которого на самом деле нет.
 *
 * Порядок ожидаемых заголовков задан явно: при `COLLATE NOCASE` русские заголовки сортируются по
 * кодам UTF-8, поэтому «Война» (0x412 0x439) идёт раньше «Воскресение» (0x412 0x441).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: BookRepository

    /** Источники и запуск сканов: только их и трогает пересканирование. */
    private val sources = FakeSourceRepository()
    private val scans = FakeScanRepository()

    /** Удаление файлов и хеши содержимого: обе операции подменяются, файловой системы в тесте нет. */
    private val files = FakeFileRemover()
    private val hashes = FakeContentHasher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepositoryImpl(
            dispatchers = TestAppDispatchers(),
            bookDao = database.bookDao(),
            overrideDao = database.metadataOverrideDao(),
            readingProgressDao = database.readingProgressDao(),
            sourceRepository = sources,
            scanRepository = scans,
            readerSession = FakeReaderSession(),
            duplicateFinder = DuplicateFinder(hashes),
            fileRemover = files,
            clock = FakeAppClock(),
            authorWriter = BookAuthorWriter(database.authorDao()),
            bookCleanupDao = database.bookCleanupDao(),
            bookCopyDao = database.bookCopyDao(),
            contentOpener = FakeBookContentOpener(),
            fb2Optimizer = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer())),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun empty_library_yields_an_empty_page_with_zero_total() = runBlocking {
        val page = repository.observePage(LibraryQuery()).first()

        assertThat(page.items).isEmpty()
        assertThat(page.totalCount).isEqualTo(0)
    }

    @Test
    fun returns_all_books_when_no_filters_are_set() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery()).first()

        assertThat(page.totalCount).isEqualTo(5)
        assertThat(page.items).hasSize(5)
    }

    @Test
    fun filters_by_author() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(DOSTOEVSKY))),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().title).isEqualTo("Преступление и наказание")
    }




    @Test
    fun filters_by_series() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(series = setOf("Классика"))),
        ).first()

        // Внутри одной серии порядок — по выпуску: «Война и мир» (1869) раньше «Анны Карениной»
        // (1877). Номера в серии у этих книг нет, поэтому решает год издания.
        assertThat(page.items.map { it.title }).isEqualTo(listOf("Война и мир", "Анна Каренина"))
    }

    @Test
    fun combines_different_facets_with_and_semantics() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY), series = setOf("Классика"))),
        ).first()

        // Внутри одной серии порядок — по выпуску: «Война и мир» (1869) раньше «Анны Карениной»
        // (1877). Номера в серии у этих книг нет, поэтому решает год издания.
        assertThat(page.items.map { it.title }).isEqualTo(listOf("Война и мир", "Анна Каренина"))
    }

    @Test
    fun several_values_inside_one_facet_use_or_semantics() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY, DOSTOEVSKY))),
        ).first()

        // 3 книги Толстого + 1 Достоевского.
        assertThat(page.totalCount).isEqualTo(4)
    }

    @Test
    fun filters_by_favorites_only() = runBlocking {
        givenLibrary()
        repository.setFavorite("war", true).assertOk()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(favoritesOnly = true)),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().title).isEqualTo("Война и мир")
    }

    @Test
    fun filters_by_reading_status() = runBlocking {
        givenLibrary()
        repository.markRead("war", ReadingStatus.READ).assertOk()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(statuses = setOf(ReadingStatus.READ))),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
    }

    @Test
    fun filters_by_format() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(formats = setOf("pdf"))),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().extension).isEqualTo("pdf")
    }

    @Test
    fun filters_by_search_term() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(searchTerm = "каренина")),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().title).isEqualTo("Анна Каренина")
    }

    @Test
    fun sorts_by_year_ascending() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery(sort = SortOrder.YEAR)).first()

        assertThat(page.items.map { it.publishedYear }).isEqualTo(listOf(1866, 1869, 1877, 1899, 1910))
    }

    @Test
    fun sorts_descending_when_asked() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery(sort = SortOrder.YEAR, descending = true)).first()

        assertThat(page.items.map { it.publishedYear }).isEqualTo(listOf(1910, 1899, 1877, 1869, 1866))
    }

    @Test
    fun sorts_by_reading_progress_using_joined_progress() = runBlocking {
        givenLibrary()
        database.readingProgressDao().upsert(ReadingProgressEntity(bookId = "war", percent = 0.75f, updatedAt = 1L))
        database.readingProgressDao().upsert(ReadingProgressEntity(bookId = "anna", percent = 0.10f, updatedAt = 1L))

        val page = repository.observePage(LibraryQuery(sort = SortOrder.PROGRESS, descending = true)).first()

        // У остальных книг прогресса нет, то есть 0; порядок среди них задаёт добивочный ключ по id.
        assertThat(page.items.map { it.id }).isEqualTo(listOf("war", "anna", "crime", "mothers", "resurrection"))
    }

    @Test
    fun pagination_returns_disjoint_stable_pages() = runBlocking {
        givenLibrary()

        val firstPage = repository.observePage(LibraryQuery(page = 0, pageSize = 2)).first()
        val secondPage = repository.observePage(LibraryQuery(page = 1, pageSize = 2)).first()
        val thirdPage = repository.observePage(LibraryQuery(page = 2, pageSize = 2)).first()

        assertThat(firstPage.items.map { it.id }).isEqualTo(listOf("anna", "war"))
        assertThat(secondPage.items.map { it.id }).isEqualTo(listOf("resurrection", "mothers"))
        assertThat(thirdPage.items.map { it.id }).isEqualTo(listOf("crime"))
        assertThat(firstPage.totalCount).isEqualTo(5)
        assertThat(secondPage.totalCount).isEqualTo(5)
    }

    @Test
    fun total_count_respects_the_same_filters_as_the_selection() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY)), pageSize = 1),
        ).first()

        assertThat(page.items).hasSize(1)
        assertThat(page.totalCount).isEqualTo(3)
        assertThat(page.hasNext).isTrue()
    }

    @Test
    fun grouping_orders_by_group_key_first() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery(grouping = Grouping.SERIES, sort = SortOrder.YEAR)).first()

        // Группы по алфавиту: «Классика», «Русская классика», «Советская».
        assertThat(page.items.first().seriesName).isEqualTo("Классика")
        assertThat(page.items.map { it.seriesName }.distinct()).hasSize(3)
    }

    @Test
    fun observes_single_book_with_its_authors() = runBlocking {
        givenLibrary()

        val book = repository.observeById("war").first()

        assertThat(book?.title).isEqualTo("Война и мир")
        assertThat(book?.authors).isEqualTo(listOf("Лев Толстой"))
    }

    @Test
    fun get_by_id_fails_for_unknown_book() = runBlocking {
        val result = repository.getById("нет-такой")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun set_favorite_changes_only_the_flag() = runBlocking {
        givenLibrary()
        val before = repository.getById("war").getOrNull()

        repository.setFavorite("war", true).assertOk()

        val after = repository.getById("war").getOrNull()
        assertThat(after?.favorite).isTrue()
        assertThat(after?.title).isEqualTo(before?.title)
    }

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

    private fun source(id: String, enabled: Boolean = true): Source = Source(
        id = id,
        uri = "content://com.android.externalstorage.documents/tree/$id",
        displayName = "Папка $id",
        kind = io.github.bookshelf.core.data.model.SourceKind.SAF_TREE,
        enabled = enabled,
    )

    @Test
    fun `book by id carries the folder and path for the card`() = runBlocking {
        givenLibrary()

        val book = repository.observeById("war").first()

        // Карточка показывает «Books / Война и мир.fb2»: путь и папка должны доходить до домена,
        // иначе строки «Путь» и «Документ» остаются пустыми (найдено 28.09.2026).
        assertThat(book?.relativePath).isEqualTo("Война и мир.fb2")
        assertThat(book?.sourceId).isEqualTo(SOURCE_ID)
    }

    private fun AppResult<Unit>.assertOk() {
        assertThat(isSuccess).isTrue()
    }

    /** Число строк связи по книге: проверяется сырым запросом, чтобы тест не зависел от DAO. */
    private fun countRows(table: String, bookId: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table WHERE bookId = ?", arrayOf(bookId))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    /** Отметить книгу открытой: так же, как это делает открытие из полки. */
    private suspend fun givenOpenedAt(bookId: String, at: Long) {
        val book = checkNotNull(database.bookDao().getById(bookId))
        database.bookDao().update(book.copy(lastOpenedAt = at))
    }

    /** Прогресс чтения книги: доля, а не проценты — так его хранит таблица. */
    private suspend fun givenProgress(bookId: String, percent: Float) {
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = bookId,
                percent = percent,
                updatedAt = 2_000L,
                source = "FBREADER_AIDL",
            ),
        )
    }

    private suspend fun givenLibrary() {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().upsertAll(
            listOf(
                book("war", "Война и мир", "Лев Толстой", series = "Классика", year = 1869, ext = "fb2"),
                book("anna", "Анна Каренина", "Лев Толстой", series = "Классика", year = 1877, ext = "epub"),
                book("crime", "Преступление и наказание", "Фёдор Достоевский", "Русская классика", 1866, "fb2"),
                book("mothers", "Матери и дети", "Максим Горький", "Советская", 1910, "pdf"),
                book("resurrection", "Воскресение", "Лев Толстой", "Русская классика", 1899, "epub"),
            ),
        )
        database.authorDao().upsertAll(
            listOf(
                AuthorEntity(id = TOLSTOY, name = "Лев Толстой", sortName = "Толстой"),
                AuthorEntity(id = DOSTOEVSKY, name = "Фёдор Достоевский", sortName = "Достоевский"),
                AuthorEntity(id = GORKY, name = "Максим Горький", sortName = "Горький"),
            ),
        )
        database.authorDao().insertBookAuthors(
            listOf(
                BookAuthorCrossRef(bookId = "war", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "anna", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "resurrection", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "crime", authorId = DOSTOEVSKY),
                BookAuthorCrossRef(bookId = "mothers", authorId = GORKY),
            ),
        )
    }

    @Test
    fun stored_share_of_reading_progress_is_exposed_as_percent() = runBlocking {
        database.bookDao().insert(book(id = "progress", title = "Книга", author = "", ext = "fb2"))
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = "progress",
                percent = 0.25f,
                updatedAt = 1_000L,
                source = "FBREADER_AIDL",
            ),
        )

        val loaded = repository.getById("progress").getOrNull()

        // В таблице хранится доля 0..1, а полка печатает значение со знаком процента: отдай мы
        // наружу долю — книга, прочитанная на четверть, показывалась бы как «читаю 0 %».
        assertThat(loaded?.progressPercent).isWithin(0.01f).of(25f)
    }

    @Test
    fun deletePermanently_removes_the_file_and_the_row() = runBlocking {
        database.bookDao().insert(book(id = "gone", title = "Книга", author = "", ext = "fb2"))
        files.refuse = null

        val result = repository.deletePermanently(listOf("gone")).getOrNull()

        assertThat(result?.deleted).isEqualTo(1)
        assertThat(result?.failed).isEqualTo(0)
        assertThat(database.bookDao().getById("gone")).isNull()
    }

    @Test
    fun deletePermanently_keeps_the_row_when_the_provider_refuses() = runBlocking {
        database.bookDao().insert(book(id = "kept", title = "Книга", author = "", ext = "fb2"))
        files.refuse = io.github.bookshelf.core.common.error.AppError.Unknown(message = "нет доступа к папке")

        val result = repository.deletePermanently(listOf("kept")).getOrNull()

        // Строку оставляем: исчезни она, следующий скан вернул бы книгу на полку, и удаление
        // выглядело бы отменившимся само.
        assertThat(result?.deleted).isEqualTo(0)
        assertThat(result?.failed).isEqualTo(1)
        assertThat(database.bookDao().getById("kept")).isNotNull()
    }

    @Test
    fun findDuplicates_reads_the_whole_library() = runBlocking {
        database.bookDao().insert(book(id = "one", title = "Книга", author = "", ext = "fb2"))
        database.bookDao().insert(book(id = "two", title = "Книга", author = "", ext = "fb2"))
        hashes.hash = "одинаковый"

        val report = repository.findDuplicates().getOrNull()

        // Обе книги одного размера и с одинаковым содержимым: одна группа, одна копия к удалению.
        assertThat(report?.groups).hasSize(1)
        assertThat(report?.duplicatesToRemove).isEqualTo(1)
    }

    /**
     * Фейк удаления файлов: помнит, что просили удалить, и умеет отказать.
     *
     * Свой интерфейс — значит фейк, а не `mockk` (ADR-012, правило 2).
     */
    private class FakeFileRemover : SafTreeWriter {

        var refuse: io.github.bookshelf.core.common.error.AppError? = null

        val deleted = mutableListOf<String>()

        override suspend fun write(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            bytes: ByteArray,
            mimeType: String,
        ): AppResult<String> = AppResult.success("content://doc/fake")

        override suspend fun writeStream(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            mimeType: String,
            onProgress: suspend (bytesWritten: Long, total: Long) -> Unit,
            block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
        ): AppResult<String> = AppResult.success("content://doc/fake")

        override suspend fun delete(documentUri: String): AppResult<Unit> {
            refuse?.let { error -> return AppResult.failure(error) }
            deleted += documentUri
            return AppResult.success(Unit)
        }
    }

    /** Фейк хеша содержимого: одна и та же строка означает «файлы идентичны». */
    private class FakeContentHasher : ContentHasher {

        var hash: String = "разный"

        override fun contentHash(documentUri: String): String = hash
    }

    @Suppress("LongParameterList")
    private fun book(
        id: String,
        title: String,
        author: String,
        series: String? = null,
        year: Int? = null,
        ext: String,
    ) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$title.$ext",
        fileName = "$title.$ext",
        extension = ext,
        mimeType = "application/$ext",
        sizeBytes = 1000L,
        lastModified = 1000L,
        title = title,
        sortTitle = title,
        authorsText = author,
        seriesName = series,
        publishedYear = year,
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )


    /** Копия, созданная читалкой: тот же файл под другим адресом. */
    private suspend fun givenCopy(bookId: String, copyUri: String) {
        database.bookCopyDao().upsert(
            BookCopyEntity(
                copyDocumentUri = copyUri,
                bookId = bookId,
                sourceId = "source-1",
                sizeBytes = 100L,
                lastModified = 100L,
                contentHash = "hash-1",
            ),
        )
    }

    private companion object {
        const val DOWNLOAD_ID = "download:one"

        const val COPY_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F2026-09-30%2Fbook.fb2.zip"

        const val SOURCE_ID = "source-1"

        // Ключ автора — значение books.authorsText: нормализованные authors/book_authors
        // приложение не заполняет, и отбор по ним не находил ни одной книги.
        const val TOLSTOY = "Лев Толстой"
        const val DOSTOEVSKY = "Фёдор Достоевский"
        const val GORKY = "Максим Горький"

        const val ALEXANDER = "Александр Тарарев"
        const val YURY = "Юрий Тарарев"

        /** Идентификатор автора — тот же, что пишет приложение: `author:` + имя в нижнем регистре. */
        fun authorIdOf(name: String): String = AUTHOR_ID_PREFIX + name.lowercase()

        const val AUTHOR_ID_PREFIX = "author:"
    }
}
