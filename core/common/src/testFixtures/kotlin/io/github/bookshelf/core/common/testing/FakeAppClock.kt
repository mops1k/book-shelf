package io.github.bookshelf.core.common.testing

import io.github.bookshelf.core.common.time.AppClock
import java.time.Instant

/**
 * Часы с управляемым временем.
 *
 * Живут в `testFixtures`, а не в `:core:testing`, потому что `:core:testing` зависит от
 * `:core:common` — использовать его в тестах самого `:core:common` было бы циклом.
 */
class FakeAppClock(private var currentMillis: Long = DEFAULT_START_MILLIS) : AppClock {

    override fun nowMillis(): Long = currentMillis

    override fun nowInstant(): Instant = Instant.ofEpochMilli(currentMillis)

    /** Сдвинуть время вперёд — так тестируются сроки «пропавших» книг и кэша. */
    fun advanceByMillis(delta: Long): Long {
        currentMillis += delta
        return currentMillis
    }

    fun setTo(epochMillis: Long) {
        currentMillis = epochMillis
    }

    companion object {
        /** 2026-01-01T00:00:00Z — фиксированная точка, чтобы тесты были воспроизводимы. */
        const val DEFAULT_START_MILLIS: Long = 1_767_225_600_000L
    }
}
