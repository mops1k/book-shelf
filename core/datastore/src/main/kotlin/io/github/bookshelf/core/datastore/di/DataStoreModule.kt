package io.github.bookshelf.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.datastore.DataStoreSettingsRepository
import io.github.bookshelf.core.datastore.SettingsKeys
import io.github.bookshelf.core.datastore.SettingsRepository
import javax.inject.Singleton

/**
 * Единственный `DataStore<Preferences>` приложения (ADR-002).
 *
 * Файл создаётся через `PreferenceDataStoreFactory.create`, а не через делегат
 * `by preferencesDataStore(...)`: делегат — свойство `Context`, и его нельзя положить в DI-граф
 * так, чтобы в тестах подменять на in-memory реализацию. Имя файла — [SettingsKeys.SETTINGS_FILE_NAME],
 * чтобы оно было ровно одно на проект.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(SettingsKeys.SETTINGS_FILE_NAME) },
        // STUB(Sprint 1): ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() },
        //  чтобы битый preferences_pb не блокировал запуск (docs/decisions.md, ADR-002).
    )
}

/** Привязка репозитория настроек: интерфейс для потребителей, реализация — DataStore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindingsModule {

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
