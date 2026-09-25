package io.github.bookshelf.core.common.log

import android.util.Log

/**
 * Реализация [AppLogger] поверх `android.util.Log`.
 *
 * STUB(Sprint 1): фильтрация по `BuildConfig.DEBUG` и по максимальному уровню из настроек
 * («Диагностика» в экране настроек) — в Спринте 5.
 */
class AndroidAppLogger : AppLogger {

    override fun v(tag: String, message: String, throwable: Throwable?) = log(Log.VERBOSE, tag, message, throwable)

    override fun d(tag: String, message: String, throwable: Throwable?) = log(Log.DEBUG, tag, message, throwable)

    override fun i(tag: String, message: String, throwable: Throwable?) = log(Log.INFO, tag, message, throwable)

    override fun w(tag: String, message: String, throwable: Throwable?) = log(Log.WARN, tag, message, throwable)

    override fun e(tag: String, message: String, throwable: Throwable?) = log(Log.ERROR, tag, message, throwable)

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) {
            Log.println(priority, tag, message)
        } else {
            Log.println(priority, tag, "$message\n${Log.getStackTraceString(throwable)}")
        }
    }
}
