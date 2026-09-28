package io.github.bookshelf.core.common.testing

import io.github.bookshelf.core.common.time.AppClock
import java.time.Instant

/**
 * Часы с управляемым временем.
 *
 * Живут в `:core:testing`, а не в `src/testFixtures`: Android-фича `testFixtures` не создаёт
 * задачу компиляции Kotlin (проверено на AGP 8.7.3 + KGP 2.0.21), поэтому `.kt` оттуда
 * молча не собираются. Модуль подключается через `testImplementation` и в production не попадает.
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
