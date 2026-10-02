package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Очистка кэша обложек из настроек (`CoverCacheClearing.kt`).
 *
 * Раньше команда была заглушкой «пока не поддерживается», хотя `CoverCache.clear()` уже был
 * реализован: проверяем именно то, что раньше не работало — вызов очистки и честный текст
 * результата. Ошибка не должна молчать или выглядеть успехом.
 */
class CoverCacheClearingTest {

    @Test
    fun `report names the freed volume`() = runBlocking {
        val cache = FakeCoverCache(sizeBefore = 5L * 1024L * 1024L)

        val report = clearCoverCacheWithReport(cache)

        assertThat(report).isEqualTo(
            AppText(R.string.settings_cover_cache_cleared, freedBytesText(5L * 1024L * 1024L)),
        )
        assertThat(cache.cleared).isTrue()
    }

    @Test
    fun `failed clearing is reported with its cause`() = runBlocking {
        val cache = FakeCoverCache(clearFailure = IOException("нет места на диске"))

        val report = clearCoverCacheWithReport(cache)

        // Текст не выдаёт сбой за успех, а причина — по типу ошибки (docs/tdd.md §7):
        // сообщение IOException («нет места на диске») человеку не показывают.
        assertThat(report).isEqualTo(
            AppText(
                R.string.settings_cover_cache_failed,
                IOException("нет места на диске").toAppError().text(),
            ),
        )
    }

    @Test
    fun `cancellation is propagated instead of being reported as a failure`() = runBlocking {
        val cache = FakeCoverCache(clearFailure = CancellationException("экран закрыт"))

        // Уход с экрана — не «не удалось очистить»: исключение обязано дойти до корутин.
        val thrown = runCatching { clearCoverCacheWithReport(cache) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    /** Кэш без диска и Android: важно только занятое место, очистка и её исход. */
    private class FakeCoverCache(
        private val sizeBefore: Long = 0L,
        private val clearFailure: Throwable? = null,
    ) : CoverCache {

        var cleared: Boolean = false
            private set

        override suspend fun read(bookId: String, kind: CoverCache.CoverKind): AppResult<File?> =
            AppResult.success(null)

        override suspend fun store(
            bookId: String,
            kind: CoverCache.CoverKind,
            bytes: ByteArray,
        ): AppResult<File> = AppResult.success(UNUSED_FILE)

        override suspend fun invalidate(bookId: String) = Unit

        override suspend fun clear() {
            clearFailure?.let { throw it }
            cleared = true
        }

        override suspend fun sizeBytes(): Long = if (cleared) 0L else sizeBefore

        override fun fileFor(bookId: String, kind: CoverCache.CoverKind): File = UNUSED_FILE

        private companion object {
            val UNUSED_FILE: File = File("unused")
        }
    }
}
