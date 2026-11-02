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
import io.github.bookshelf.core.datastore.update.DataStoreUpdateStore
import io.github.bookshelf.core.datastore.update.UpdateKeys
import io.github.bookshelf.core.datastore.update.UpdateStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** Отдельный файл DataStore для отметки проверок обновлений (ADR-018). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateDataStore

/**
 * Хранилище отметки проверок обновлений.
 *
 * Фабрика та же, что у лаунчера: обработчик битого файла и диспетчеры не должны расходиться
 * между хранилищами, а отдельный файл спасает остальные настройки от повреждения.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateDataStoreModule {

    @Provides
    @Singleton
    @UpdateDataStore
    fun provideUpdateDataStore(
        @ApplicationContext context: Context,
        dispatchers: AppDispatchers,
    ): DataStore<Preferences> = createLauncherDataStore(
        produceFile = { context.preferencesDataStoreFile(UpdateKeys.FILE_NAME) },
        dispatchers = dispatchers,
    )
}

/** Привязка хранилища проверок обновлений. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindingsModule {

    @Binds
    abstract fun bindUpdateStore(impl: DataStoreUpdateStore): UpdateStore
}
