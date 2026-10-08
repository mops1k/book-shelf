package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
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
 * Очистка истории загрузок.
 *
 * Требование Александра 30.09.2026: на экране загрузок нужна кнопка «Очистить историю».
 * Проверяется то, что легко сделать неправильно: история убирается только после подтверждения
 * (на e-ink палец легко попадает дважды, а вернуть список загрузок нечем), а «Отмена» не трогает
 * репозиторий вовсе.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogsClearHistoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOpdsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `history is cleared only after confirmation`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onToggleClearHistory()
        assertThat(viewModel.uiState.value.isConfirmingClearHistory).isTrue()

        // Повторное нажатие — «передумал»: подтверждение убирается, репозиторий не трогается.
        viewModel.onToggleClearHistory()
        assertThat(viewModel.uiState.value.isConfirmingClearHistory).isFalse()
        // Отмена — это отмена: до репозитория запрос не доходит.
        assertThat(repository.clearHistoryCalls).isEqualTo(0)

        viewModel.onToggleClearHistory()
        viewModel.onConfirmClearHistory()
        advanceUntilIdle()

        assertThat(repository.clearHistoryCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.isConfirmingClearHistory).isFalse()
    }

    @Test
    fun `a second confirmation during the call does not start a second cleanup`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onToggleClearHistory()
        viewModel.onConfirmClearHistory()
        viewModel.onConfirmClearHistory()
        advanceUntilIdle()

        assertThat(repository.clearHistoryCalls).isEqualTo(1)
    }

    private fun viewModel() = CatalogsViewModel(
        opdsRepository = repository,
        sourceRepository = FakeSourceRepository,
        catalogFeedRequest = CatalogFeedRequest(),
        loadedEntries = LoadedFeedEntries(),
    )
}
