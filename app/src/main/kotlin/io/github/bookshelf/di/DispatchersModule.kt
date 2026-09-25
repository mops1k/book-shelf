package io.github.bookshelf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.di.DefaultDispatcher
import io.github.bookshelf.core.common.di.IoDispatcher
import io.github.bookshelf.core.common.di.MainDispatcher
import io.github.bookshelf.core.common.di.MetadataDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Раздача диспетчеров по квалификаторам.
 *
 * Отдельный модуль нужен потому, что `AppDispatchers` — единый объект, а потребителям
 * требуются конкретные диспетчеры: иначе каждый модуль тащил бы весь контейнер.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(dispatchers: AppDispatchers): CoroutineDispatcher = dispatchers.io

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(dispatchers: AppDispatchers): CoroutineDispatcher = dispatchers.default

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(dispatchers: AppDispatchers): CoroutineDispatcher = dispatchers.main

    @Provides
    @Singleton
    @MetadataDispatcher
    fun provideMetadataDispatcher(dispatchers: AppDispatchers): CoroutineDispatcher = dispatchers.metadata
}
