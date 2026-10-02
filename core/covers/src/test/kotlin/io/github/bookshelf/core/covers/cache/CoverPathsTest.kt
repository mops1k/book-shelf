package io.github.bookshelf.core.covers.cache

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.covers.CoverCache.CoverKind
import java.io.File
import org.junit.Test

/**
 * Тест имён и путей кэша обложек («Архитектура» §5).
 *
 * Главное здесь — не имена (они тривиальны), а защита от `bookId`, который пытается выйти за
 * пределы `filesDir/covers`. Идентификатор приходит из БД, но кэш не имеет права доверять данным
 * слепо: импортированная библиотека может содержать что угодно.
 */
class CoverPathsTest {

    @Test
    fun covers_dir_is_inside_files_dir() {
        assertThat(CoverPaths.coversDir(File("/data/data/app/files")))
            .isEqualTo(File("/data/data/app/files/covers"))
    }

    @Test
    fun file_names_match_documented_scheme() {
        assertThat(CoverPaths.fileName("abc-123", CoverKind.FULL)).isEqualTo("abc-123.webp")
        assertThat(CoverPaths.fileName("abc-123", CoverKind.TILE)).isEqualTo("abc-123_t.webp")
    }

    @Test
    fun file_for_is_built_from_cache_dir() {
        val cacheDir = File("/data/data/app/files/covers")

        assertThat(CoverPaths.fileFor(cacheDir, "abc-123", CoverKind.FULL))
            .isEqualTo(File("/data/data/app/files/covers/abc-123.webp"))
        assertThat(CoverPaths.fileFor(cacheDir, "abc-123", CoverKind.TILE))
            .isEqualTo(File("/data/data/app/files/covers/abc-123_t.webp"))
    }

    @Test
    fun temp_file_is_sibling_of_target() {
        val target = File("/data/data/app/files/covers/abc-123.webp")

        assertThat(CoverPaths.tempFileFor(target).path)
            .isEqualTo("/data/data/app/files/covers/abc-123.webp.tmp")
    }

    @Test
    fun safe_book_ids_are_accepted() {
        assertThat(CoverPaths.isSafeBookId("abc-123")).isTrue()
        assertThat(CoverPaths.isSafeBookId("0f8fad5b-d9cb-469f-a165-70867728950e")).isTrue()
        assertThat(CoverPaths.isSafeBookId("book_1.v2")).isTrue()
    }

    @Test
    fun traversal_and_separators_are_rejected() {
        assertThat(CoverPaths.isSafeBookId("")).isFalse()
        assertThat(CoverPaths.isSafeBookId("..")).isFalse()
        assertThat(CoverPaths.isSafeBookId("../etc/passwd")).isFalse()
        assertThat(CoverPaths.isSafeBookId("a/b")).isFalse()
        assertThat(CoverPaths.isSafeBookId("a\\b")).isFalse()
        assertThat(CoverPaths.isSafeBookId("/absolute")).isFalse()
        assertThat(CoverPaths.isSafeBookId(".hidden")).isFalse()
        assertThat(CoverPaths.isSafeBookId("a..b")).isFalse()
    }

    @Test
    fun unsafe_book_id_fails_loudly_instead_of_writing_outside_cache() {
        val thrown = runCatching { CoverPaths.fileName("../escape", CoverKind.FULL) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }
}
