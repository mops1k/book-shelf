package io.github.bookshelf.feature.filters

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.testing.fake.FakeCollectionRepository
import io.github.bookshelf.core.testing.fake.FakeFilterRepository

/**
 * Редактор правил умной полки.
 *
 * Проверяется то, из-за чего редактор вообще имеет смысл: правила полки становятся начальным
 * выбором, правки живут локально (не трогая фильтр библиотеки) и сохраняются в полку JSON-ом.
 * Отдельно — что пустой черновик не сохраняется: полка без условий показывает пустоту, а не
 * всю библиотеку (см. `SmartRules.isEmpty`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfRulesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shelf rules become the initial draft`() = runTest(dispatcher) {
        val fixture = Fixture(SmartRules(tags = setOf("sf_space")).toJson())

        val viewModel = fixture.viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.shelfName).isEqualTo("Космос")
        assertThat(viewModel.uiState.value.selected.tags).containsExactly("sf_space")
    }

    @Test
    fun `toggling a facet updates the draft and recounts the shelf`() = runTest(dispatcher) {
        val fixture = Fixture(null)

        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        viewModel.onToggleFacet(FacetKind.AUTHORS, "Лем Станислав")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selected.authors).containsExactly("Лем Станислав")
        // Фасеты и число книг считаются по черновику: пользователь видит, что даст правило.
        assertThat(fixture.facetQueries.last().authorNames).containsExactly("Лем Станислав")
        assertThat(viewModel.uiState.value.resultCount).isEqualTo(2)
    }

    @Test
    fun `saving writes the draft as shelf rules`() = runTest(dispatcher) {
        val fixture = Fixture(null)

        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        viewModel.onToggleFacet(FacetKind.TAGS, "sf_space")
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(fixture.updateCalls).isEqualTo(1)
        assertThat(SmartRules.fromJson(fixture.savedRulesJson)?.tags).containsExactly("sf_space")
        assertThat(viewModel.uiState.value.saved).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `empty draft is not saved as rules`() = runTest(dispatcher) {
        val fixture = Fixture(SmartRules(tags = setOf("sf_space")).toJson())

        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        viewModel.onReset()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        // Пустые правила означают пустую полку: сохранить их значило бы «стереть» полку молча.
        assertThat(fixture.updateCalls).isEqualTo(0)
        assertThat(viewModel.uiState.value.saved).isFalse()
        // В баннер идёт ресурс, а не техническое пояснение нижнего слоя: оно не переводится.
        assertThat(viewModel.uiState.value.error).isEqualTo(AppText(R.string.shelf_rules_error_empty))
    }

    @Test
    fun `shelf updates do not overwrite the draft`() = runTest(dispatcher) {
        val fixture = Fixture(null)

        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        viewModel.onToggleFacet(FacetKind.AUTHORS, "Лем Станислав")
        advanceUntilIdle()

        // Список полок обновляется и по другим причинам (например, изменилось число книг):
        // повторное применение правил стёрло бы уже сделанный выбор.
        fixture.shelves.value = fixture.shelves.value.map { shelf -> shelf.copy(bookCount = 5) }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selected.authors).containsExactly("Лем Станислав")
    }

    @Test
    fun `missing shelf is reported instead of showing empty rules`() = runTest(dispatcher) {
        val fixture = Fixture(null)
        fixture.shelves.value = emptyList()

        val viewModel = fixture.viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(AppText(R.string.shelf_rules_error_not_found))
    }

    @Test
    fun `a failed save shows a translatable text, not the technical one`() = runTest(dispatcher) {
        val failure = AppError.Unknown(message = "SQLiteException: disk I/O error (code 778)")
        val fixture = Fixture(SmartRules(tags = setOf("sf_space")).toJson(), AppResult.failure(failure))

        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        // Причина — текст по типу ошибки: сообщение нижнего слоя («SQLiteException…») человеку не показывают.
        assertThat(viewModel.uiState.value.error)
            .isEqualTo(AppText(R.string.shelf_rules_error_save, failure.text()))
    }

    /** Полка, репозитории и записанные вызовы — в одном месте, чтобы тесты читались как сценарий. */
    private class Fixture(rulesJson: String?, private val updateResult: AppResult<Unit> = AppResult.success(Unit)) {

        /** Состав полок — тот же поток, что видит фейк: тест меняет его и после создания фикстуры. */
        val shelves: MutableStateFlow<List<Collection>>
            get() = collections.shelvesFlow

        /**
         * Репозитории — фейками, а не моками (ADR-012, правило 2). Проверки читаются как
         * утверждения о данных: «по такому запросу считали фасеты», «вот эти правила записаны».
         */
        private val collections = FakeCollectionRepository()
        private val filters = FakeFilterRepository()
        private val books = FakeBookRepository(
            pageFlow = flowOf(
                PageResult(items = emptyList<Book>(), totalCount = 2, offset = 0, limit = 1),
            ),
        )

        /** Запросы, по которым редактор считал фасеты: по ним видно, что черновик дошёл до данных. */
        val facetQueries: List<BookQuery> get() = filters.facetQueries

        /** Правила, записанные в полку; `null` — сохранения не было. */
        val savedRulesJson: String? get() = collections.savedRules?.second

        /** Сколько раз правила пытались сохранить. */
        val updateCalls: Int get() = collections.updateSmartRulesCalls

        init {
            shelves.value = listOf(
                Collection(
                    id = SHELF_ID,
                    name = "Космос",
                    type = CollectionType.SMART,
                    rulesJson = rulesJson,
                ),
            )
            collections.updateResult = updateResult
            filters.facetsFlow = flowOf(
                FacetSet(
                    authors = listOf(
                        FacetItem(id = "Лем Станислав", labelCode = "Лем Станислав", count = 2),
                    ),
                ),
            )
        }

        fun viewModel(): ShelfRulesViewModel = ShelfRulesViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ShelfRulesRoute.ARG_COLLECTION_ID to SHELF_ID)),
            collectionRepository = collections,
            filterRepository = filters,
            bookRepository = books,
        )
    }

    private companion object {
        const val SHELF_ID = "shelf-1"
    }
}
