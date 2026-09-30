package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Поиск копии уже известной книги по имени файла и размеру.
 *
 * Зачем запрос: FBReader, открывая книгу из нашего приложения, копирует файл в `Books/<дата>/`
 * внутри нашей библиотеки (его настройка «Куда сохранять загруженные книги» указывает на ту же
 * папку, что мы сканируем). Копия — тот же файл, поэтому у неё совпадают имя и размер; заводить на
 * неё вторую книгу нельзя — 30.09.2026 так появился дубль в полке.
 *
 * Проверяются границы: совпало и имя, и размер — копия; отличается размер — другая книга;
 * отличается имя — другая книга. Без этого «защита от копий» прятала бы настоящие новые книги.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class KnownBookCopyQueryTest {

    private lateinit var database: BookshelfDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a file with the same name and size as a known book is found`() = runBlocking {
        database.sourceDao().insert(BookDaoTestFixtures.source(id = "src-1"))
        database.bookDao().insert(
            BookDaoTestFixtures.book(
                id = "doc:original",
                sourceId = "src-1",
                title = "Предыстория. Книга 4",
                extension = "fb2.zip",
                sizeBytes = 588_846L,
            ),
        )

        val copies = database.bookDao()
            .countByFileNameAndSize(COPY_NAME, 588_846L, excludeId = "doc:copy")

        assertThat(copies).isEqualTo(1)
    }

    @Test
    fun `a file of another size is not a copy`() = runBlocking {
        database.sourceDao().insert(BookDaoTestFixtures.source(id = "src-1"))
        database.bookDao().insert(
            BookDaoTestFixtures.book(
                id = "doc:original",
                sourceId = "src-1",
                title = "Предыстория. Книга 4",
                extension = "fb2.zip",
                sizeBytes = 588_846L,
            ),
        )

        val copies = database.bookDao()
            .countByFileNameAndSize(COPY_NAME, 588_847L, excludeId = "doc:copy")

        assertThat(copies).isEqualTo(0)
    }

    @Test
    fun `a file with another name is not a copy`() = runBlocking {
        database.sourceDao().insert(BookDaoTestFixtures.source(id = "src-1"))
        database.bookDao().insert(
            BookDaoTestFixtures.book(
                id = "doc:original",
                sourceId = "src-1",
                title = "Предыстория. Книга 4",
                extension = "fb2.zip",
                sizeBytes = 588_846L,
            ),
        )

        val copies = database.bookDao()
            .countByFileNameAndSize("Другая книга.fb2.zip", 588_846L, excludeId = "doc:copy")

        assertThat(copies).isEqualTo(0)
    }

    @Test
    fun `the book being written is not counted as its own copy`() = runBlocking {
        database.sourceDao().insert(BookDaoTestFixtures.source(id = "src-1"))
        database.bookDao().insert(
            BookDaoTestFixtures.book(
                id = "doc:same",
                sourceId = "src-1",
                title = "Предыстория. Книга 4",
                extension = "fb2.zip",
                sizeBytes = 588_846L,
            ),
        )

        val copies = database.bookDao()
            .countByFileNameAndSize(COPY_NAME, 588_846L, excludeId = "doc:same")

        assertThat(copies).isEqualTo(0)
    }

    private companion object {
        const val COPY_NAME = "Предыстория. Книга 4.fb2.zip"
    }
}
