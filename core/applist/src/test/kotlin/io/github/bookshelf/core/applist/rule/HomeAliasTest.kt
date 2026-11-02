package io.github.bookshelf.core.applist.rule

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Имя компонента alias HOME (ADR-015, фаза 7).
 *
 * Как и у alias «Приложения»: класс от namespace, пакет от сборки. Ошибка здесь означает, что
 * выключение режима лаунчера не освободит роль домашнего экрана.
 */
class HomeAliasTest {

    @Test
    fun home_alias_class_comes_from_namespace() {
        assertThat(HomeAlias.CLASS_NAME).isEqualTo("io.github.bookshelf.apps.HomeAlias")
    }

    @Test
    fun debug_build_component_name_keeps_application_id_as_package() {
        assertThat(HomeAlias.fullName("io.github.bookshelf.debug"))
            .isEqualTo("io.github.bookshelf.debug/io.github.bookshelf.apps.HomeAlias")
    }

    @Test
    fun release_build_component_name_uses_release_package() {
        assertThat(HomeAlias.fullName("io.github.bookshelf"))
            .isEqualTo("io.github.bookshelf/io.github.bookshelf.apps.HomeAlias")
    }
}
