package io.github.bookshelf.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.coroutine.DefaultAppDispatchers
import io.github.bookshelf.core.common.log.AndroidAppLogger
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.common.time.SystemAppClock
import javax.inject.Singleton

/**
 * Базовые зависимости, от которых зависит всё остальное.
 *
 * Здесь только те вещи, которые обязаны быть подменяемыми в тестах: время, диспетчеры, лог.
 * Именно поэтому они интерфейсы, а не `object`/`System.currentTimeMillis()` в месте вызова.
 */
@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = AndroidAppLogger()

    @Provides
    @Singleton
    fun provideAppClock(): AppClock = SystemAppClock

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = DefaultAppDispatchers()

    // STUB(Sprint 3): параллелизм парсеров должен читаться из настроек
    //  (SettingsRepository.metadataParallelism, по умолчанию 2), а DefaultAppDispatchers —
    //  перестраиваться при изменении настройки без перезапуска приложения.
}
