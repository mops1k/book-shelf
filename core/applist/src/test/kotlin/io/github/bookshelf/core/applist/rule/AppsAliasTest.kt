package io.github.bookshelf.core.applist.rule

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Имя компонента alias (ADR-015, фаза 6).
 *
 * Пакет — от сборки (`io.github.bookshelf.debug` у отладки), класс — от namespace
 * (`io.github.bookshelf`). Ошибка в любой половине означает выключение не того компонента,
 * поэтому имя закреплено тестом и продублировано в `docs/TESTING.md`.
 */
class AppsAliasTest {

    @Test
    fun alias_class_comes_from_namespace_not_from_application_id() {
        assertThat(AppsAlias.CLASS_NAME).isEqualTo("io.github.bookshelf.apps.AppsAlias")
    }

    @Test
    fun debug_build_component_name_keeps_application_id_as_package() {
        assertThat(AppsAlias.fullName("io.github.bookshelf.debug"))
            .isEqualTo("io.github.bookshelf.debug/io.github.bookshelf.apps.AppsAlias")
    }

    @Test
    fun release_build_component_name_uses_release_package() {
        assertThat(AppsAlias.fullName("io.github.bookshelf"))
            .isEqualTo("io.github.bookshelf/io.github.bookshelf.apps.AppsAlias")
    }
}
