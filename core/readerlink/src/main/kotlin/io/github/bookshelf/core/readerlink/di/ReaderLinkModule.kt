package io.github.bookshelf.core.readerlink.di

import android.content.Context
import dagger.Binds
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.readerlink.fbreader.BinderFbReaderTransport
import io.github.bookshelf.core.readerlink.fbreader.DefaultFbReaderApi
import io.github.bookshelf.core.readerlink.fbreader.FbReaderAdapter
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import io.github.bookshelf.core.readerlink.fbreader.ReadingProgressSource
import javax.inject.Singleton
import io.github.bookshelf.core.readerlink.discovery.AndroidReaderDiscovery
import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.fbreader.DefaultFbReaderConnection
import io.github.bookshelf.core.readerlink.fbreader.FbReaderConnection
import io.github.bookshelf.core.readerlink.launch.DefaultReaderLauncher
import io.github.bookshelf.core.readerlink.launch.ReaderLauncher
import io.github.bookshelf.core.readerlink.profile.DefaultReaderProfileRepository
import io.github.bookshelf.core.readerlink.profile.ReaderProfileRepository
import io.github.bookshelf.core.readerlink.resolve.DefaultReaderResolver
import io.github.bookshelf.core.readerlink.resolve.ReaderResolver

/**
 * DI-модуль интеграции с читалками.
 *
 * Все интерфейсы связываются с заглушками, которые падают с `NotImplementedError` при вызове:
 * так граф зависимостей проверяется компилятором уже сейчас, а реализация наполняется в
 * Sprint 5 (L1) и Sprint 7 (L2/L3). `ReadingProgressSource`/`FbReaderAdapter` здесь намеренно
 * не связываются — для них сначала нужен `FbReaderApi` (см. `src/main/aidl/README.md`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderLinkModule {

    @Binds
    abstract fun bindReaderProfileRepository(
        impl: DefaultReaderProfileRepository,
    ): ReaderProfileRepository

    @Binds
    abstract fun bindReaderDiscovery(
        impl: AndroidReaderDiscovery,
    ): ReaderDiscovery

    @Binds
    abstract fun bindReaderResolver(
        impl: DefaultReaderResolver,
    ): ReaderResolver

    @Binds
    abstract fun bindReaderLauncher(
        impl: DefaultReaderLauncher,
    ): ReaderLauncher

    companion object {

        /**
         * Клиент API читалки: транспорт через `bindService` и разбор ответов по кодам.
         *
         * Единственная реализация, и она же единственное место, где приложение знает про действия
         * и коды FBReader (проверены на устройстве 29.09.2026).
         */
        @Provides
        @Singleton
        fun provideFbReaderApi(
            @ApplicationContext context: Context,
            dispatchers: AppDispatchers,
        ): FbReaderApi = DefaultFbReaderApi(BinderFbReaderTransport(context, dispatchers))

        /** Доменный источник прогресса: считает процент по данным читалки. */
        @Provides
        @Singleton
        fun provideReadingProgressSource(api: FbReaderApi): ReadingProgressSource = FbReaderAdapter(api)
    }

    @Binds
    abstract fun bindFbReaderConnection(
        impl: DefaultFbReaderConnection,
    ): FbReaderConnection
}
