package io.github.bookshelf.core.data.repository.impl

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.core.readerlink.profile.ReaderProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Профили читалок — таблица `reader_profiles` и `format_defaults`.
 *
 * Дефект, ради которого репозиторий реализован: интерфейс был объявлен, а реализация состояла из
 * одной заглушки, бросающей `NotImplementedError`. Любой путь, который бы до неё дошёл (выбор
 * читалки, «открыть в…»), падал бы — то есть обещанное хранение профилей в базе не работало вовсе.
 *
 * Проверяется здесь ровно то, чем профили отличаются от простого списка: встроенные preset нельзя
 * удалить, читалка ищется по пакету (индекс), для формата хранится свой выбор, а список для
 * человека содержит только включённые профили.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ReaderProfileRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var profiles: ReaderProfileRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookshelfDatabase::class.java,
        ).allowMainThreadQueries().build()
        profiles = ReaderProfileRepositoryImpl(
            profileDao = database.readerProfileDao(),
            formatDefaultDao = database.formatDefaultDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saved profile comes back whole`() = runTest {
        profiles.upsert(PROFILE)

        val stored = profiles.observeEnabled().first().single()

        assertThat(stored).isEqualTo(PROFILE)
        // Список MIME едет строкой через разделитель — проверяется его сохранность.
        assertThat(stored.mimeTypes).containsExactly("application/fb2", "application/epub+zip").inOrder()
    }

    @Test
    fun `only enabled profiles are offered to the person`() = runTest {
        profiles.upsert(PROFILE)
        profiles.upsert(PROFILE.copy(id = "off", name = "Выключенная", packageName = "ru.off", enabled = false))

        val offered = profiles.observeEnabled().first()

        assertThat(offered.map { it.name }).containsExactly(PROFILE.name)
    }

    @Test
    fun `a more important reader stands higher in the list`() = runTest {
        profiles.upsert(PROFILE.copy(id = "low", name = "Ниже", packageName = "ru.low", priority = 0))
        profiles.upsert(PROFILE.copy(id = "high", name = "Выше", packageName = "ru.high", priority = 10))

        val offered = profiles.observeEnabled().first()

        assertThat(offered.map { it.name }).containsExactly("Выше", "Ниже").inOrder()
    }

    @Test
    fun `an updated profile replaces the previous one`() = runTest {
        profiles.upsert(PROFILE)
        profiles.upsert(PROFILE.copy(name = "Новое имя", priority = 5))

        val stored = profiles.observeEnabled().first().single()

        assertThat(stored.name).isEqualTo("Новое имя")
        assertThat(stored.priority).isEqualTo(5)
    }

    @Test
    fun `a built-in profile cannot be deleted`() = runTest {
        profiles.upsert(PROFILE.copy(isBuiltIn = true))

        profiles.delete(PROFILE.id)

        // Иначе человек одним нажатием снёс бы preset, который приложение ставит само.
        assertThat(profiles.observeEnabled().first()).hasSize(1)
    }

    @Test
    fun `a profile the person added is deleted`() = runTest {
        profiles.upsert(PROFILE.copy(isBuiltIn = false))

        profiles.delete(PROFILE.id)

        assertThat(profiles.observeEnabled().first()).isEmpty()
    }

    @Test
    fun `a profile is found by the package of the installed reader`() = runTest {
        profiles.upsert(PROFILE)

        val found = profiles.findByPackage(PROFILE.packageName)

        assertThat(found).isEqualTo(PROFILE)
        assertThat(profiles.findByPackage("ru.unknown")).isNull()
    }

    @Test
    fun `the reader chosen for a format is stored and returned`() = runTest {
        profiles.upsert(PROFILE)

        profiles.setDefaultForFormat(format = "epub", profileId = PROFILE.id)

        assertThat(profiles.defaultForFormat("epub")).isEqualTo(PROFILE)
        assertThat(profiles.defaultForFormat("fb2")).isNull()
    }

    @Test
    fun `a format default pointing at a deleted profile gives nothing`() = runTest {
        // Строка `format_defaults` не имеет внешнего ключа: профиль мог быть удалён отдельно,
        // и вместо падения разбор обязан вернуть «читалки нет».
        database.formatDefaultDao().upsert(
            io.github.bookshelf.core.database.entity.FormatDefaultEntity(format = "epub", profileId = "gone"),
        )

        assertThat(profiles.defaultForFormat("epub")).isNull()
    }

    private companion object {
        val PROFILE = ReaderProfile(
            id = "fbreader",
            name = "FBReader",
            packageName = "org.geometerplus.zlibrary.ui.android",
            activityName = null,
            mimeTypes = listOf("application/fb2", "application/epub+zip"),
            supportsContentUri = true,
            extrasJson = """{"key":"value"}""",
            isBuiltIn = true,
            enabled = true,
            priority = 1,
        )
    }
}
