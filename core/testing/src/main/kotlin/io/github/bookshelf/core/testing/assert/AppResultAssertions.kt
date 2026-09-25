package io.github.bookshelf.core.testing.assert

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult

/**
 * Проверки результата операции.
 *
 * Репозитории и парсеры возвращают `AppResult`, поэтому в тестах нужны короткие проверки:
 * основной сценарий проверяет значение, а тест на ошибку — конкретный тип ошибки,
 * а не «что-то пошло не так».
 */
fun <T> AppResult<T>.assertSuccess(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw AssertionError("Ожидался успех, получена ошибка: $error")
}

fun <T> AppResult<T>.assertFailure(): AppError = when (this) {
    is AppResult.Success -> throw AssertionError("Ожидалась ошибка, получено значение: $value")
    is AppResult.Failure -> error
}

inline fun <reified E : AppError> AppResult<*>.assertFailureOfType(): E {
    val error = assertFailure()
    if (error !is E) {
        throw AssertionError("Ожидалась ошибка ${E::class.simpleName}, получена $error")
    }
    return error
}
