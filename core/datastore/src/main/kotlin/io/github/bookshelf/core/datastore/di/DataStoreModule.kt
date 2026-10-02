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
import io.github.bookshelf.core.datastore.DataStoreSettingsRepository
import io.github.bookshelf.core.datastore.SettingsKeys
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.createSettingsDataStore
import javax.inject.Singleton

/**
 * Единственный `DataStore<Preferences>` приложения (ADR-002).
 *
 * Файл создаётся через [createSettingsDataStore], а не через делегат `by preferencesDataStore(...)`:
 * делегат — свойство `Context`, и его нельзя положить в DI-граф так, чтобы в тестах подменять на
 * in-memory реализацию. Имя файла — [SettingsKeys.SETTINGS_FILE_NAME], чтобы оно было ровно одно
 * на проект. Обработчик битого файла настроен внутри фабрики, чтобы боевой и тестовый экземпляры
 * вели себя одинаково.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        dispatchers: AppDispatchers,
    ): DataStore<Preferences> = createSettingsDataStore(
        produceFile = { context.preferencesDataStoreFile(SettingsKeys.SETTINGS_FILE_NAME) },
        dispatchers = dispatchers,
    )
}

/** Привязка репозитория настроек: интерфейс для потребителей, реализация — DataStore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindingsModule {

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
