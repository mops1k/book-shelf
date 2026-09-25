package io.github.bookshelf.core.common.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Результат операции: либо значение, либо [AppError].
 *
 * Заменяет `kotlin.Result`, потому что нам нужен собственный тип ошибки вместо `Throwable`
 * и предсказуемое поведение в `suspend`-функциях.
 */
sealed interface AppResult<out T> {

    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>

    companion object {
        fun <T> success(value: T): AppResult<T> = Success(value)
        fun <T> failure(error: AppError): AppResult<T> = Failure(error)
        fun <T> failure(message: String, cause: Throwable? = null): AppResult<T> =
            Failure(AppError.Unknown(message = message, cause = cause))
    }
}

val AppResult<*>.isSuccess: Boolean get() = this is AppResult.Success

val AppResult<*>.isFailure: Boolean get() = this is AppResult.Failure

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

fun AppResult<*>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error

fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> T): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> fallback(error)
}

fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

fun <T, R> AppResult<T>.fold(onSuccess: (T) -> R, onFailure: (AppError) -> R): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(error)
}

/**
 * Аналог [runCatching], но:
 * * не глотает [CancellationException] — отмена корутины обязана пробрасываться;
 * * возвращает [AppError], а не произвольный `Throwable`.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> appRunCatching(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(throwable.toAppError())
}

@Suppress("TooGenericExceptionCaught")
fun Throwable.toAppError(): AppError = when (this) {
    is java.io.FileNotFoundException -> AppError.NotFound(what = message ?: "file", message = message)
    is SecurityException -> AppError.PermissionDenied(message = message)
    is java.io.IOException -> AppError.Io(message = message, cause = this)
    else -> AppError.Unknown(message = message, cause = this)
}
