package io.github.bookshelf

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bookshelf.core.common.app.FolderOpener
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.designsystem.theme.EinkTheme
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.testing.fake.FakeCollectionRepository
import io.github.bookshelf.core.testing.fake.FakeReaderRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import io.github.bookshelf.feature.bookdetails.BookDetailsRoute
import io.github.bookshelf.feature.bookdetails.BookDetailsViewModel
import io.github.bookshelf.feature.bookdetails.ui.BookDetailsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Подтверждение удаления на карточке книги («E-ink UX» §6.4).
 *
 * ДЕФЕКТ (Александр, 02.10.2026, найден при проверке удаления): строка подтверждения выше кнопки,
 * которую она заменяет, а содержимое карточки остаётся на прежней прокрутке. Низ строки уходил за
 * границу окна — кнопки «Удалить»/«Отмена» обрезались до полоски, и тап по этой полоске попадал
 * в закреплённый подвал «Читать»: вместо подтверждения удаления открывалась читалка.
 *
 * Проверяется здесь, а не юнит-тестом: дефект в разметке, и виден он только при настоящем
 * измерении и обрезке окном («TDD» §4). Окно задано заведомо ниже карточки, поэтому
 * дефект воспроизводится на любом устройстве, а не только на узком экране.
 */
@RunWith(AndroidJUnit4::class)
class BookDetailsConfirmInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun the_confirmation_buttons_are_fully_visible_without_scrolling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deleteForever = context.getString(
            io.github.bookshelf.feature.bookdetails.R.string.book_action_delete_forever,
        )
        val confirm = context.getString(io.github.bookshelf.feature.bookdetails.R.string.book_action_delete)
        val cancel = context.getString(io.github.bookshelf.core.designsystem.R.string.eink_action_cancel)
        val viewModel = viewModel()

        composeRule.setContent {
            EinkTheme {
                BookDetailsScreen(
                    bookId = BOOK_ID,
                    onBack = {},
                    onRead = {},
                    onOpenCollections = {},
                    onEditMetadata = {},
                    onOpenSeriesBook = {},
                    onOpenLibrary = {},
                    // Окно заведомо ниже карточки: так дефект виден на любом устройстве.
                    modifier = Modifier.requiredSize(width = 400.dp, height = 640.dp),
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(deleteForever)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Строка подтверждения обязана оказаться в зоне видимости целиком: касательная зона —
        // не меньше 48 dp («E-ink UX» §2). Обрезанная до полоски кнопка порог не проходит.
        composeRule.onNodeWithText(confirm)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(TOUCH_TARGET)
        composeRule.onNodeWithText(cancel)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(TOUCH_TARGET)
    }

    private fun viewModel(): BookDetailsViewModel = BookDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(BookDetailsRoute.ARG_BOOK_ID to BOOK_ID)),
        bookRepository = FakeBookRepository(byIdFlow = MutableStateFlow(book())),
        readerRepository = FakeReaderRepository(),
        metadataScheduler = NoopScheduler(),
        collectionRepository = FakeCollectionRepository(),
        browseRequest = LibraryBrowseRequest(),
        sourceRepository = FakeSourceRepository(),
        folderOpener = FolderOpener { AppResult.success(Unit) },
    )

    private fun book() = Book(
        id = BOOK_ID,
        title = "Солярис",
        authorsText = "Станислав Лем",
        extension = "fb2",
        sizeBytes = 1_024L,
        addedAt = 1_000L,
    )

    /** Разбор в этой проверке не запускается: карточка только просит его по команде. */
    private class NoopScheduler : MetadataScheduler {
        override suspend fun enqueueMetadata(bookId: String, force: Boolean, refreshCover: Boolean) = Unit
    }

    private companion object {
        const val BOOK_ID = "doc:one"

        /** Минимальная касательная зона («E-ink UX» §2). */
        val TOUCH_TARGET = 48.dp
    }
}
