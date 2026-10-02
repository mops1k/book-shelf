package io.github.bookshelf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.flags.BuildConfigFeatureFlags
import javax.inject.Singleton

/**
 * Flavor-флаги. Отдельный модуль, а не общий [AppModule]: это точка, где сборка влияет
 * на поведение приложения, и её полезно видеть отдельно (ADR-007, «Архитектура» §9).
 */
@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsModule {

    @Provides
    @Singleton
    fun provideFeatureFlags(): FeatureFlags = BuildConfigFeatureFlags()
}
