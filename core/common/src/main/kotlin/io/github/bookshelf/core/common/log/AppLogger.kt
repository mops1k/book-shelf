package io.github.bookshelf.core.common.log

/**
 * Логирование без сетевой аналитики и крашлитики: приложение полностью офлайн,
 * поэтому весь лог уходит только в logcat (см. `«Архитектура»` §1).
 *
 * Проект оборачивает `android.util.Log` своим интерфейсом, чтобы:
 * * в тестах подменять реализацию на записывающую;
 * * в release-сборке держать единую точку отключения подробных логов.
 */
interface AppLogger {

    fun v(tag: String, message: String, throwable: Throwable? = null)

    fun d(tag: String, message: String, throwable: Throwable? = null)

    fun i(tag: String, message: String, throwable: Throwable? = null)

    fun w(tag: String, message: String, throwable: Throwable? = null)

    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/** Тег по умолчанию для логов приложения. */
const val APP_LOG_TAG: String = "BookShelf"

/** Удобная точка входа: `log.d("Скан", "…")` без указания тега. */
fun AppLogger.debug(message: String, throwable: Throwable? = null) = d(APP_LOG_TAG, message, throwable)

fun AppLogger.info(message: String, throwable: Throwable? = null) = i(APP_LOG_TAG, message, throwable)

fun AppLogger.warn(message: String, throwable: Throwable? = null) = w(APP_LOG_TAG, message, throwable)

fun AppLogger.error(message: String, throwable: Throwable? = null) = e(APP_LOG_TAG, message, throwable)
