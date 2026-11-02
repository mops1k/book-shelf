package io.github.bookshelf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.BuildConfig
import io.github.bookshelf.core.update.model.AppBuildInfo
import io.github.bookshelf.core.update.model.AppFlavor
import javax.inject.Singleton

/**
 * Что за сборка запущена — для проверки обновлений (ADR-018).
 *
 * Единственное место, где flavor превращается в [AppFlavor]: `:core:update` о `BuildConfig`
 * не знает и знать не должен. Сборка `play` получает [AppFlavor.PLAY] — самообновление в ней
 * запрещено правилами магазина.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideAppBuildInfo(): AppBuildInfo = AppBuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        flavor = when (BuildConfig.FLAVOR) {
            "fdroid" -> AppFlavor.FDROID
            "play" -> AppFlavor.PLAY
            else -> AppFlavor.FULL
        },
    )
}
