package io.github.bookshelf.feature.collections

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.data.removable.RemovableShelf
import io.github.bookshelf.core.data.removable.volumeTreeUri
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import io.github.bookshelf.core.storage.volume.RemovableVolume
import io.github.bookshelf.core.testing.fake.FakeCollectionRepository
import io.github.bookshelf.core.testing.fake.FakeRemovableShelfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Полка съёмного носителя на экране полок (требование Александра 01.10.2026).
 *
 * Проверяются повороты, которые видит человек: вставил карту — в полках появилась строка носителя с
 * числом книг; доступа нет — экран предлагает его дать и открывает системный выбор папки; человек
 * выбрал папку — источник заведён; выбрал не то или отказался — экран честно говорит, что не вышло.
 * Носителя нет или настройка выключена — строки нет вовсе (это решает репозиторий, экран верит ему).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val shelves = FakeCollectionRepository()
    private val card = FakeRemovableShelfRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a card with books becomes a shelf row`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 3, needsAccess = false)

        val viewModel = viewModel()
        advanceUntilIdle()

        val row = viewModel.uiState.value.removableShelves.single()
        assertThat(row.uuid).isEqualTo(CARD.uuid)
        assertThat(row.label).isEqualTo(CARD.label)
        assertThat(row.bookCount).isEqualTo(3)
        assertThat(row.needsAccess).isFalse()
        // Выбор папки никто не запрашивал: доступ уже есть.
        assertThat(viewModel.uiState.value.cardAccessRequest).isNull()
    }

    @Test
    fun `no card means no shelf row`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.removableShelves).isEmpty()
    }

    @Test
    fun `every partition of the medium gets its own row`() = runTest(dispatcher) {
        val second = RemovableVolume(uuid = "0436-C297", label = "disk", path = "/mnt/media_rw/0436-C297")
        card.shelvesFlow.value = listOf(shelf(bookCount = 3, needsAccess = false), shelfOf(second, bookCount = 1))

        val viewModel = viewModel()
        advanceUntilIdle()

        val rows = viewModel.uiState.value.removableShelves
        assertThat(rows.map { row -> row.uuid }).containsExactly(CARD.uuid, second.uuid).inOrder()
        assertThat(rows.map { row -> row.bookCount }).containsExactly(3, 1).inOrder()
    }

    @Test
    fun `direct access is taken without asking for a folder`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 0, needsAccess = true)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onGrantCardAccess(CARD.uuid)
        advanceUntilIdle()

        assertThat(card.requested).containsExactly(CARD.uuid)
        // Доступ ко всем файлам уже выдан: вопрос человеку был бы лишним (решение 01.10.2026).
        assertThat(viewModel.uiState.value.cardAccessRequest).isNull()
        assertThat(viewModel.uiState.value.cardError).isNull()
    }

    @Test
    fun `without all files access the screen opens the folder picker`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 0, needsAccess = true)
        card.ensureResult = AppResult.failure(AppError.PermissionDenied(message = "нет доступа"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onGrantCardAccess(CARD.uuid)
        advanceUntilIdle()

        // Пикер открывается на корне носителя, а не на последней папке: искать карту вручную — работа.
        assertThat(viewModel.uiState.value.cardAccessRequest).isEqualTo(volumeTreeUri(CARD.uuid))
        assertThat(viewModel.uiState.value.cardError).isNull()
    }

    @Test
    fun `the folder chosen by the person grants access to the card`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 0, needsAccess = true)
        card.ensureResult = AppResult.failure(AppError.PermissionDenied(message = "нет доступа"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantCardAccess(CARD.uuid)
        advanceUntilIdle()
        val picked = "${volumeTreeUri(CARD.uuid)}Books"

        viewModel.onCardTreePicked(picked)
        advanceUntilIdle()

        assertThat(card.treeGrants).containsExactly(CARD.uuid to picked)
        assertThat(viewModel.uiState.value.cardAccessRequest).isNull()
        assertThat(viewModel.uiState.value.cardError).isNull()
    }

    @Test
    fun `closing the picker leaves the shelf waiting for access`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 0, needsAccess = true)
        card.ensureResult = AppResult.failure(AppError.PermissionDenied(message = "нет доступа"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantCardAccess(CARD.uuid)
        advanceUntilIdle()

        viewModel.onCardTreePicked(null)
        advanceUntilIdle()

        // Отказ человека — не ошибка: он вернётся к строке носителя и попробует снова.
        assertThat(card.treeGrants).isEmpty()
        assertThat(viewModel.uiState.value.cardAccessRequest).isNull()
        assertThat(viewModel.uiState.value.cardError).isNull()
    }

    @Test
    fun `a folder outside the card is reported`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 0, needsAccess = true)
        card.ensureResult = AppResult.failure(AppError.PermissionDenied(message = "нет доступа"))
        card.treeResult = AppResult.failure(AppError.InvalidArgument(what = "treeUri"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onGrantCardAccess(CARD.uuid)
        advanceUntilIdle()

        viewModel.onCardTreePicked("content://com.android.externalstorage.documents/tree/primary%3ABooks")
        advanceUntilIdle()

        // Экран покажет текст по типу ошибки, а не готовую строку: код ошибки переводится ресурсом.
        assertThat(viewModel.uiState.value.cardError)
            .isInstanceOf(AppError.InvalidArgument::class.java)
    }

    @Test
    fun `a failed direct path is reported`() = runTest(dispatcher) {
        card.shelf = shelf(bookCount = 0, needsAccess = true)
        card.ensureResult = AppResult.failure(AppError.Io(message = "нет доступа к дереву"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onGrantCardAccess(CARD.uuid)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.cardError).isInstanceOf(AppError.Io::class.java)
        assertThat(viewModel.uiState.value.cardAccessRequest).isNull()
    }

    @Test
    fun `a failed action names both the action and the reason as resources`() = runTest(dispatcher) {
        shelves.updateResult = AppResult.failure(AppError.PermissionDenied(message = "URI permission expired"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDelete(SHELF_ID)
        advanceUntilIdle()

        // Оба текста — ресурсы («TDD» §5), а техническое сообщение нижнего слоя не показывается (§7):
        // иначе на английском интерфейсе в баннере была бы русская диагностика провайдера.
        assertThat(viewModel.uiState.value.error).isEqualTo(
            AppText(R.string.collections_error_delete, AppError.PermissionDenied().text()),
        )
    }

    @Test
    fun `a blank name is refused with its own text`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRename(SHELF_ID, "   ")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(AppText(R.string.collections_error_rename_empty))
        // До репозитория дело не дошло: переименовывать нечем.
        assertThat(shelves.renamed).isEmpty()
    }

    @Test
    fun `an empty draft filter is refused with its own text`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSaveCurrentFilter("Умная полка")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(AppText(R.string.collections_error_filter_empty))
        assertThat(shelves.created).isEmpty()
    }

    @Test
    fun `adding a preset writes the preset title into the database`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAddPreset("favorites", "Избранное")
        advanceUntilIdle()

        // Решение Александра 02.10.2026: название пресета пишется в базу готовым текстом
        // (сознательное сужение правила «текст — ресурс»: при смене языка не переведётся).
        assertThat(shelves.created.map { (name, _) -> name }).containsExactly("Избранное")
        assertThat(shelves.created.single().second).isEqualTo(CollectionType.SMART)
    }

    @Test
    fun `a recipe already in the database is not offered again`() = runTest(dispatcher) {
        // Полка с пустым именем и правилом «избранное»: имя её не выдаёт, выдаёт правило.
        shelves.shelves = listOf(
            Collection(
                id = "c1",
                name = "",
                type = CollectionType.SMART,
                bookCount = 3,
                rulesJson = SmartRules(favoritesOnly = true).toJson(),
            ),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.presets.map { preset -> preset.id })
            .doesNotContain("favorites")
    }

    @Test
    fun `unmounting a volume asks the screen for the system storage page`() = runTest(dispatcher) {
        card.shelvesFlow.value = listOf(
            RemovableShelf(volume = CARD, sourceId = "card-source", bookCount = 3, needsAccess = false),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onUnmountCard(CARD.uuid)

        // Просьба передаётся экрану: системный интент собирается там, где есть `Context`.
        assertThat(viewModel.uiState.value.cardUnmountRequest).isEqualTo(CARD.uuid)

        viewModel.onUnmountLaunched()
        assertThat(viewModel.uiState.value.cardUnmountRequest).isNull()
    }

    private fun viewModel() = CollectionsViewModel(
        collectionRepository = shelves,
        filterSelection = FilterSelection(),
        removableShelfRepository = card,
    )

    private fun shelf(bookCount: Int, needsAccess: Boolean) =
        shelfOf(CARD, bookCount = bookCount, needsAccess = needsAccess)

    private fun shelfOf(
        volume: RemovableVolume,
        bookCount: Int,
        needsAccess: Boolean = true,
    ) = RemovableShelf(
        volume = volume,
        sourceId = if (needsAccess) null else "card-source",
        bookCount = bookCount,
        needsAccess = needsAccess,
    )

    private companion object {
        const val SHELF_ID = "shelf-1"

        val CARD = RemovableVolume(uuid = "1A2B-3C4D", label = "SD-карта", path = "/storage/1A2B-3C4D")
    }
}
