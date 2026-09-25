package io.github.bookshelf.core.common.time

import java.time.Instant

/**
 * Часы приложения.
 *
 * Нужны интерфейсом, а не `System.currentTimeMillis()`, потому что от времени зависят
 * инкрементальный скан, срок жизни `isMissing` и сортировки — всё это тестируется
 * с фиксированным временем (`FakeAppClock` в `:core:testing`).
 */
interface AppClock {
    fun nowMillis(): Long
    fun nowInstant(): Instant = Instant.ofEpochMilli(nowMillis())
}

object SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
