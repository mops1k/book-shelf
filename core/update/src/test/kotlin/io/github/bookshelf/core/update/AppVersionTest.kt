package io.github.bookshelf.core.update

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.update.model.AppVersion
import org.junit.Test

/**
 * Версия релиза: разбор тега и сравнение (ADR-018).
 *
 * Сравнивать `versionCode` нельзя — он внутри APK и до скачивания недоступен, поэтому решение
 * «новее ли» принимается по тегу релиза (`v2.0.1`).
 */
class AppVersionTest {

    @Test
    fun parses_tag_with_v_prefix() {
        assertThat(AppVersion.parse("v2.0.1")).isEqualTo(AppVersion(2, 0, 1))
    }

    @Test
    fun parses_plain_version() {
        assertThat(AppVersion.parse("2.0.1")).isEqualTo(AppVersion(2, 0, 1))
    }

    @Test
    fun parses_version_with_suffix_after_dash() {
        // Суффикс сборки (2.0.1-debug) к сравнению версий отношения не имеет.
        assertThat(AppVersion.parse("v2.0.1-debug")).isEqualTo(AppVersion(2, 0, 1))
    }

    @Test
    fun rejects_non_numeric_tag() {
        assertThat(AppVersion.parse("latest")).isNull()
        assertThat(AppVersion.parse("")).isNull()
        assertThat(AppVersion.parse("v2.x.1")).isNull()
    }

    @Test
    fun compares_versions_by_segments() {
        assertThat(AppVersion(2, 0, 1) > AppVersion(2, 0, 0)).isTrue()
        assertThat(AppVersion(2, 1, 0) > AppVersion(2, 0, 9)).isTrue()
        assertThat(AppVersion(3, 0, 0) > AppVersion(2, 9, 9)).isTrue()
        assertThat(AppVersion(2, 0, 1) == AppVersion(2, 0, 1)).isTrue()
        assertThat(AppVersion(2, 0, 1) > AppVersion(2, 0, 1)).isFalse()
    }

    @Test
    fun renders_canonical_string() {
        assertThat(AppVersion(2, 0, 1).toString()).isEqualTo("2.0.1")
    }
}
