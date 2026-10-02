package io.github.bookshelf.core.common.coroutine

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

/**
 * Пул разбора метаданных: обещание «на e-ink одновременно разбирается не больше двух книг».
 *
 * Обещание важное и легко теряется: стоит убрать `limitedParallelism` или пустить разбор на пул
 * WorkManager — и на e-ink читалка начнёт получать меньше процессора, чем записано в
 * «Архитектура» §4.4. Поэтому здесь проверяется факт ограничения, а не факт вызова.
 *
 * Потоки настоящие, как в бою: подменять диспетчер в таком тесте нечем — проверяются как раз
 * размер пула и предел параллелизма.
 */
class DefaultAppDispatchersTest {

    @Test
    fun `no more parses run at once than the pool allows`() {
        val dispatchers = DefaultAppDispatchers(parallelism = PARALLELISM)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val limitReached = CountDownLatch(PARALLELISM)
        val release = CountDownLatch(1)

        val workers = (1..TASKS).map { index ->
            Thread({ holdParse(dispatchers, running, peak, limitReached, release) }, "test-parse-$index")
                .apply { start() }
        }

        // Как только заявленный предел занят, остальные задачи обязаны ждать очереди.
        limitReached.await()
        assertThat(running.get()).isAtMost(PARALLELISM)

        release.countDown()
        workers.forEach { it.join() }

        assertThat(peak.get()).isAtMost(PARALLELISM)
        assertThat(peak.get()).isEqualTo(PARALLELISM)
        dispatchers.close()
    }

    @Test
    fun `parsing threads belong to the metadata pool`() {
        val dispatchers = DefaultAppDispatchers(parallelism = PARALLELISM)

        val names = runBlocking {
            (1..TASKS).map { withContext(dispatchers.metadata) { Thread.currentThread().name } }
        }

        // Имя потока — то, по чему разбор видно в отладке и в /proc: bookshelf-metadata.
        names.forEach { name -> assertThat(name).startsWith("bookshelf-metadata") }
        dispatchers.close()
    }

    private fun holdParse(
        dispatchers: AppDispatchers,
        running: AtomicInteger,
        peak: AtomicInteger,
        limitReached: CountDownLatch,
        release: CountDownLatch,
    ) {
        runBlocking {
            withContext(dispatchers.metadata) {
                val now = running.incrementAndGet()
                peak.updateAndGet { current -> maxOf(current, now) }
                limitReached.countDown()
                // Поток пула занят «разбором»: без блокировки ограничение не проверить.
                release.await()
                running.decrementAndGet()
            }
        }
    }

    private companion object {
        const val PARALLELISM = 2
        const val TASKS = 4
    }
}
