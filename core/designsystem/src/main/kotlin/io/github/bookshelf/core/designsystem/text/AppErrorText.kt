package io.github.bookshelf.core.designsystem.text

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.designsystem.R

/**
 * Текст ошибки для человека: по типу, а не по [AppError.message] («TDD» §7, решение 01.10.2026).
 *
 * Почему так, а не «показать message»:
 * * `message` — диагностика нижнего слоя («Не удалось прочитать /storage/…/книга.fb2»); она рождается
 *   вне UI и не переводится, поэтому на английском интерфейсе человек увидел бы русскую строку;
 * * у каждого типа ошибки есть свой понятный текст, а значимые данные (формат файла, имя
 *   неподдерживаемого случая, код ответа сервера) передаются аргументами;
 * * текст — [AppText], а не готовая строка: это ресурс, значит его переводит система.
 *
 * `message` при этом не теряется: он остаётся в самом значении ошибки (`toString()`), и его видит
 * тот, кто пишет ошибку в лог.
 */
fun AppError.text(): AppText = when (this) {
    is AppError.NotFound -> AppText(R.string.error_not_found)
    is AppError.InvalidArgument -> AppText(R.string.error_invalid_argument)
    is AppError.PermissionDenied -> AppText(R.string.error_permission_denied)
    AppError.AccessLost -> AppText(R.string.error_access_lost)
    is AppError.Io -> AppText(R.string.error_io)
    is AppError.Parse -> AppText(R.string.error_parse, format)
    is AppError.Network -> httpCode?.let { code -> AppText(R.string.error_network_code, code) }
        ?: AppText(R.string.error_network)

    is AppError.Unauthorized -> AppText(R.string.error_unauthorized)
    is AppError.Unsupported -> AppText(R.string.error_unsupported, what)
    is AppError.Unknown -> AppText(R.string.error_unknown)
}
