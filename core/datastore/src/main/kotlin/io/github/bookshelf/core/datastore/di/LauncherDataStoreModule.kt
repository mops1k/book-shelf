package io.github.bookshelf.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.datastore.createLauncherDataStore
import io.github.bookshelf.core.datastore.launcher.DataStoreLauncherStore
import io.github.bookshelf.core.datastore.launcher.LauncherKeys
import io.github.bookshelf.core.datastore.launcher.LauncherStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** Отдельный файл DataStore для состояния лаунчера (ADR-015), не общий с настройками. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LauncherDataStore

/**
 * Хранилище состояния лаунчера.
 *
 * Почему отдельный файл, а не ключи в настройках: битый JSON списка приложений не должен уносить
 * тему и папки-источники, а обработчик битого файла у каждого хранилища свой.
 */
@Module
@InstallIn(SingletonComponent::class)
object LauncherDataStoreModule {

    @Provides
    @Singleton
    @LauncherDataStore
    fun provideLauncherDataStore(
        @ApplicationContext context: Context,
        dispatchers: AppDispatchers,
    ): DataStore<Preferences> = createLauncherDataStore(
        produceFile = { context.preferencesDataStoreFile(LauncherKeys.LAUNCHER_FILE_NAME) },
        dispatchers = dispatchers,
    )
}

/** Привязка хранилища лаунчера: интерфейс для потребителей, реализация — DataStore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherBindingsModule {

    @Binds
    abstract fun bindLauncherStore(impl: DataStoreLauncherStore): LauncherStore
}
