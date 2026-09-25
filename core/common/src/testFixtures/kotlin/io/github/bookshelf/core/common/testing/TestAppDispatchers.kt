package io.github.bookshelf.core.common.testing

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Диспетчеры для тестов: всё выполняется на одном тестовом диспетчере, поэтому
 * `runTest` управляет временем, а тесты не «мигают» на многопоточности.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestAppDispatchers(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : AppDispatchers {

    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val main: CoroutineDispatcher = testDispatcher
    override val metadata: CoroutineDispatcher = StandardTestDispatcher(testDispatcher.scheduler)
}
