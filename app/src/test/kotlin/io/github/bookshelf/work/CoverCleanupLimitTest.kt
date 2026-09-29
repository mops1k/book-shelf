package io.github.bookshelf.work

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.megabytesToBytes
import org.junit.Test

/**
 * Лимит уборки кэша обложек (`CoverCleanupWorker.kt`).
 *
 * Проверяется чистая часть работы — какой лимит уходит в `evictToLimit`. Сам `CoroutineWorker`
 * тут не поднимается: ему нужен WorkManager, а вся логика решения «настройка или резерв» вынесена
 * в функцию и не требует устройства.
 */
class CoverCleanupLimitTest {

    @Test
    fun `limit comes from the setting`() {
        val settings = LibrarySettings(coverCacheLimitMb = 200)

        assertThat(coverCacheLimitBytes(settings)).isEqualTo(megabytesToBytes(200))
    }

    @Test
    fun `unreadable settings fall back to the previous constant`() {
        // Настройки недоступны: 128 МБ — прежнее поведение, а не «без лимита».
        assertThat(coverCacheLimitBytes(null)).isEqualTo(FALLBACK_COVER_CACHE_LIMIT_BYTES)
        assertThat(FALLBACK_COVER_CACHE_LIMIT_BYTES).isEqualTo(128L * 1024L * 1024L)
    }

    @Test
    fun `zero limit means the cache is emptied, not kept`() {
        // 0 — это «вытеснить всё»: молча пропустить уборку при таком значении было бы ошибкой
        // «без лимита», и кэш остался бы на диске целиком.
        assertThat(coverCacheLimitBytes(LibrarySettings(coverCacheLimitMb = 0))).isEqualTo(0L)
    }
}
