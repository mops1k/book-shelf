package io.github.bookshelf.core.common.error

/**
 * Текст ошибки для экрана.
 *
 * У [AppError] поле [AppError.message] заполнено не всегда, поэтому пустое сообщение заменяется
 * описанием по типу: без этого пользователь увидел бы пустой баннер ошибки. Одна реализация на
 * все экраны — до неё эта функция лежала копией в каждой ViewModel, которая умела показывать ошибку.
 */
fun AppError.uiText(): String = message ?: when (this) {
    is AppError.Unsupported -> "пока не поддерживается ($what)"
    is AppError.NotFound -> "не найдено: $what"
    is AppError.InvalidArgument -> "неверное значение: $what"
    is AppError.PermissionDenied -> "нет доступа"
    is AppError.Io -> "ошибка ввода-вывода"
    is AppError.Parse -> "не удалось разобрать данные: $format"
    is AppError.Network -> "ошибка сети" + (httpCode?.let { " (HTTP $it)" } ?: "")
    is AppError.Unauthorized -> "нужна авторизация"
    AppError.AccessLost -> "доступ к источнику потерян"
    is AppError.Unknown -> "неизвестная ошибка"
}
