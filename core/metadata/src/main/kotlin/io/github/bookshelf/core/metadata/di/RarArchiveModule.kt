package io.github.bookshelf.core.metadata.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.bookshelf.core.metadata.rar.RarArchive

/**
 * Объявление набора реализаций чтения RAR.
 *
 * Без него Dagger считает `Set<RarArchive>` отсутствующей связью в сборках, где никто ничего
 * в набор не добавил: `fdroid` и `play` собираются без junrar (ADR-007), и их вариант пуст.
 * Пустой набор — нормальное состояние, а не ошибка: парсер `CbrParser` видит его и честно
 * не предлагает формат. Реализацию приносит `:app` из flavor-источника `src/full`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RarArchiveModule {

    @Multibinds
    abstract fun rarArchives(): Set<RarArchive>
}
