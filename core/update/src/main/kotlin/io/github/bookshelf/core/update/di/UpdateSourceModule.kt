package io.github.bookshelf.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.update.source.GitHubReleaseSource
import io.github.bookshelf.core.update.source.ReleaseSource

/**
 * Источник обновлений: боевой GitHub за портом (ADR-018).
 *
 * Проверка обновлений зависит от [ReleaseSource], а не от GitHub напрямую — в тестах порт
 * подменяется фейком, и ни один тест не ходит в сеть.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateSourceModule {

    @Binds
    abstract fun bindReleaseSource(impl: GitHubReleaseSource): ReleaseSource
}
