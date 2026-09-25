package io.github.bookshelf.core.readerlink.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    @Binds
    abstract fun bindFbReaderConnection(
        impl: DefaultFbReaderConnection,
    ): FbReaderConnection
}
