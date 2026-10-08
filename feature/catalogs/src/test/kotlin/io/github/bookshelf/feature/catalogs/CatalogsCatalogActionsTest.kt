package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.OpdsCatalog
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
 * Действия над каталогом: «⋯» → «Изменить» и «Удалить».
 *
 * Требование Александра 30.09.2026: каталог можно было только добавить — ни удалить, ни
 * переименовать, ни поменять адрес. Проверяется то, что легко сделать неправильно: форма правки
 * заполняется текущими значениями (и НЕ подставляет пароль), удаление спрашивает подтверждение, а
 * «Отмена» ничего не трогает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogsCatalogActionsTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOpdsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.catalogs = listOf(
            OpdsCatalog(
                id = CATALOG_ID,
                title = "Flibusta",
                url = "https://host/opds",
                username = "reader",
                requiresAuth = true,
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `menu opens for one catalog and closes by the second tap`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.catalogActions.onToggleActions(CATALOG_ID)
        assertThat(viewModel.uiState.value.actionsForCatalogId).isEqualTo(CATALOG_ID)

        viewModel.catalogActions.onToggleActions(CATALOG_ID)
        assertThat(viewModel.uiState.value.actionsForCatalogId).isNull()
    }

    @Test
    fun `edit opens the form with the current values and without the password`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.catalogActions.onAskEdit(CATALOG_ID)

        val state = viewModel.uiState.value
        assertThat(state.mode).isEqualTo(CatalogsMode.ADD_FORM)
        assertThat(state.editingCatalogId).isEqualTo(CATALOG_ID)
        assertThat(state.addForm.title).isEqualTo("Flibusta")
        assertThat(state.addForm.url).isEqualTo("https://host/opds")
        assertThat(state.addForm.username).isEqualTo("reader")
        // Пароль в форму не возвращается: пустое поле означает «оставить прежний».
        assertThat(state.addForm.password).isEmpty()
    }

    @Test
    fun `saving changes sends the new values and the entered password`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.catalogActions.onAskEdit(CATALOG_ID)
        viewModel.onFormChange(
            viewModel.uiState.value.addForm.copy(title = "Флибуста", password = "new-secret"),
        )

        viewModel.catalogActions.onSaveChanges()
        advanceUntilIdle()

        val (catalog, password) = repository.catalogUpdates.single()
        assertThat(catalog.id).isEqualTo(CATALOG_ID)
        assertThat(catalog.title).isEqualTo("Флибуста")
        assertThat(password).isEqualTo("new-secret")
        // После сохранения экран возвращается к списку, форма закрывается.
        assertThat(viewModel.uiState.value.mode).isEqualTo(CatalogsMode.LIST)
        assertThat(viewModel.uiState.value.editingCatalogId).isNull()
    }

    @Test
    fun `saving without a password keeps the stored one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.catalogActions.onAskEdit(CATALOG_ID)

        viewModel.catalogActions.onSaveChanges()
        advanceUntilIdle()

        assertThat(repository.catalogUpdates.single().second).isNull()
    }

    @Test
    fun `deleting asks for confirmation first`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.catalogActions.onAskDelete(CATALOG_ID)
        assertThat(viewModel.uiState.value.confirmingDeleteCatalogId).isEqualTo(CATALOG_ID)
        assertThat(repository.removedCatalogs).isEmpty()

        viewModel.catalogActions.onCancelDelete()
        assertThat(viewModel.uiState.value.confirmingDeleteCatalogId).isNull()
        assertThat(repository.removedCatalogs).isEmpty()

        viewModel.catalogActions.onAskDelete(CATALOG_ID)
        viewModel.catalogActions.onConfirmDelete()
        advanceUntilIdle()

        assertThat(repository.removedCatalogs).containsExactly(CATALOG_ID)
    }

    private fun viewModel() = CatalogsViewModel(
        opdsRepository = repository,
        sourceRepository = FakeSourceRepository,
        catalogFeedRequest = CatalogFeedRequest(),
        loadedEntries = LoadedFeedEntries(),
    )

    private companion object {
        const val CATALOG_ID = FakeOpdsRepository.CATALOG_ID
    }
}
