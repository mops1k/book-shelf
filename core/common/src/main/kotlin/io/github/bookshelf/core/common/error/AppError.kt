package io.github.bookshelf.core.common.error

/**
 * Доменные ошибки приложения.
 *
 * Библиотека принципиально не бросает исключения наружу (см. `docs/architecture.md` §1):
 * любая проблема описывается значением [AppError], а не падением.
 */
sealed interface AppError {

    /** Человекочитаемое пояснение для UI; null — показываем общий текст по типу ошибки. */
    val message: String?

    /** Запись/файл/каталог не найден. */
    data class NotFound(val what: String, override val message: String? = null) : AppError

    /**
     * Значение не проходит проверку: вне диапазона, неизвестная константа, пустая строка там,
     * где она обязательна.
     *
     * Отдельный тип нужен, чтобы UI показал «оценка должна быть от 0 до 5» рядом с полем ввода,
     * а не общее «что-то пошло не так». Проверка живёт в вызывающем слое (репозитории), потому
     * что только он знает допустимые пределы.
     */
    data class InvalidArgument(
        val what: String,
        override val message: String? = null,
    ) : AppError

    /** Нет разрешения на доступ (SAF/URI/уведомления). */
    data class PermissionDenied(val uri: String? = null, override val message: String? = null) : AppError

    /**
     * Persistable-разрешение на источник потеряно (папка удалена или перемещена).
     * Книги не удаляются, а помечаются `isMissing`. См. `docs/architecture.md` §4.1.
     */
    data object AccessLost : AppError {
        override val message: String? = null
    }

    /** Ошибка ввода-вывода. */
    data class Io(override val message: String? = null, val cause: Throwable? = null) : AppError

    /** Ошибка разбора формата: книга остаётся в библиотеке с `parseState = FAILED`. */
    data class Parse(
        val format: String,
        override val message: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    /** Сетевая ошибка (только OPDS). */
    data class Network(
        val httpCode: Int? = null,
        override val message: String? = null,
        val cause: Throwable? = null,
    ) : AppError

    /** Каталог OPDS требует авторизации или учётные данные неверны. */
    data class Unauthorized(val catalogId: String? = null, override val message: String? = null) : AppError

    /** Формат/операция не поддерживаются сборкой (например, CBR вне flavor `full`). */
    data class Unsupported(val what: String, override val message: String? = null) : AppError

    /** Всё остальное — не должно происходить, но обязано быть обработано. */
    data class Unknown(override val message: String? = null, val cause: Throwable? = null) : AppError
}
