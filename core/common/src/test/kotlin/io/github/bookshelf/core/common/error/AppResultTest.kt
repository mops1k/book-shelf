package io.github.bookshelf.core.common.error

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Первые тесты проекта. `AppResult` — фундамент: на нём построены все репозитории,
 * поэтому его поведение зафиксировано тестами до появления потребителей (TDD, docs/tdd.md).
 */
class AppResultTest {

    @Test
    fun success_holds_value() {
        val result: AppResult<Int> = AppResult.success(42)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(42)
        assertThat(result.errorOrNull()).isNull()
    }

    @Test
    fun failure_holds_error() {
        val error = AppError.NotFound(what = "book:42")
        val result: AppResult<Int> = AppResult.failure(error)

        assertThat(result.isFailure).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()).isEqualTo(error)
    }

    @Test
    fun map_transforms_success_and_keeps_failure() {
        val mapped = AppResult.success(2).map { it * 21 }
        val untouched: AppResult<Int> = AppResult.failure(AppError.AccessLost)

        assertThat(mapped.getOrNull()).isEqualTo(42)
        assertThat(untouched.map { it * 2 }.errorOrNull()).isEqualTo(AppError.AccessLost)
    }

    @Test
    fun flat_map_does_not_wrap_twice() {
        val chained = AppResult.success(2).flatMap { AppResult.success(it + 1) }

        assertThat(chained.getOrNull()).isEqualTo(3)
    }

    @Test
    fun get_or_else_uses_fallback_only_on_failure() {
        val fallback: (AppError) -> Int = { -1 }

        assertThat(AppResult.success(7).getOrElse(fallback)).isEqualTo(7)
        assertThat(AppResult.failure(AppError.AccessLost).getOrElse(fallback)).isEqualTo(-1)
    }

    @Test
    fun app_run_catching_maps_exceptions_to_app_error() {
        val result = appRunCatching<Int> { throw java.io.FileNotFoundException("нет файла") }

        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun app_run_catching_does_not_swallow_cancellation() {
        var thrown = false
        try {
            appRunCatching<Int> { throw kotlinx.coroutines.CancellationException("отмена") }
        } catch (expected: kotlinx.coroutines.CancellationException) {
            thrown = true
        }

        assertThat(thrown).isTrue()
    }
}
