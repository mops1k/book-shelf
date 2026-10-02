package io.github.bookshelf.feature.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.testing.fake.FakeReaderRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
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
 * Имя источника — данные, а не текст интерфейса (docs/tdd.md §8).
 *
 * Дефект, ради которого это появилось: когда имя папки не выводилось из адреса, в базу писалось
 * русское «Папка» — и в английском интерфейсе строка источника оставалась русской. Теперь в таком
 * случае в базу уходит пустое имя, а слова подставляет экран (ресурс «Папка» / “Folder”).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSourceNameTest {

    private val dispatcher = StandardTestDispatcher()
    private val sources = FakeSourceRepository()
    private val readers = FakeReaderRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a folder without a readable name is stored without words from code`() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(sourceRepository = sources, readerRepository = readers)

        // Адрес без последнего сегмента: имя папки узнать не из чего.
        viewModel.onSourcePicked(uri = "content://", displayName = "")
        advanceUntilIdle()

        assertThat(sources.added["content://"]).isEmpty()
    }

    @Test
    fun `the name from the system wins, and the address is the fallback`() = runTest(dispatcher) {
        val viewModel = OnboardingViewModel(sourceRepository = sources, readerRepository = readers)

        viewModel.onSourcePicked(uri = "content://tree/42", displayName = "Books")
        advanceUntilIdle()
        viewModel.onSourcePicked(uri = "content://tree/43", displayName = "")
        advanceUntilIdle()

        // Имя, которое дала система, — данные и берётся как есть; без него — последний сегмент адреса.
        assertThat(sources.added["content://tree/42"]).isEqualTo("Books")
        assertThat(sources.added["content://tree/43"]).isEqualTo("43")
    }
}
