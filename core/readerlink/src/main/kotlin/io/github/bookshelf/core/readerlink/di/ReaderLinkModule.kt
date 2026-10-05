package io.github.bookshelf.core.readerlink.di

import android.content.Context
import dagger.Binds
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.readerlink.fbreader.BinderFbReaderTransport
import io.github.bookshelf.core.readerlink.fbreader.DefaultFbReaderApi
import io.github.bookshelf.core.readerlink.fbreader.FbReaderAdapter
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import io.github.bookshelf.core.readerlink.coolreader.CoolReaderProgressSource
import io.github.bookshelf.core.readerlink.koreader.KoReaderProgressSource
import io.github.bookshelf.core.readerlink.pocketbook.PocketBookProgressSource
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import javax.inject.Singleton
import io.github.bookshelf.core.readerlink.discovery.AndroidReaderDiscovery
import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.fbreader.DefaultFbReaderConnection
import io.github.bookshelf.core.readerlink.fbreader.FbReaderConnection
import io.github.bookshelf.core.readerlink.launch.DefaultReaderLauncher
import io.github.bookshelf.core.readerlink.launch.ReaderLauncher
import io.github.bookshelf.core.readerlink.resolve.DefaultReaderResolver
import io.github.bookshelf.core.readerlink.resolve.ReaderResolver

/**
 * DI-модуль интеграции с читалками.
 *
 * Часть интерфейсов связывается с заглушками, которые падают с `NotImplementedError` при вызове:
 * так граф зависимостей проверяется компилятором, а реализация наполняется по мере надобности.
 * Источники прогресса (`ReadingProgressSource`) — набор (`@IntoSet`): читалок с доступным прогрессом
 * несколько, и добавление каждой не должно менять ни сценарий синхронизации, ни его тесты.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderLinkModule {

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
            logger: AppLogger,
        ): FbReaderApi = DefaultFbReaderApi(BinderFbReaderTransport(context, dispatchers, logger))
    }

    @Binds
    abstract fun bindFbReaderConnection(
        impl: DefaultFbReaderConnection,
    ): FbReaderConnection

    /**
     * Источники прогресса — набор, а не один биндинг.
     *
     * Так добавление читалки (KOReader, дальше CoolReader, PocketBook, Librera, XReader) не меняет
     * ни сценарий синхронизации, ни его тесты: новый источник просто появляется в
     * `ReadingProgressSources`. Собирается набор в реестр (`ReadingProgressSources`), который и
     * решает, кого спрашивать во время чтения.
     */
    @Binds
    @IntoSet
    abstract fun bindFbReaderProgressSource(impl: FbReaderAdapter): ReadingProgressSource

    /**
     * KOReader: прогресс из sidecar-файла рядом с книгой.
     *
     * Работает во всех flavor'ах — файл лежит внутри папки библиотеки, открытой пользователем, —
     * поэтому источник доступен и там, где нет доступа ко всей памяти (flavor `play`). Портовую
     * реализацию чтения файла (`ProgressFileReader`) привязывает `:core:data`: там живут SAF и
     * файловая система, о которых модуль читалок не знает.
     */
    @Binds
    @IntoSet
    abstract fun bindKoReaderProgressSource(impl: KoReaderProgressSource): ReadingProgressSource

    /**
     * CoolReader: прогресс из его базы `/sdcard/.cr3/cr3db.sqlite`.
     *
     * Нужен доступ ко всей памяти (flavor `full`/`fdroid`): база лежит вне папки библиотеки. Порт
     * чтения базы (`ProgressDatabase`) привязан в `:core:data` — там SQLite и кэш.
     */
    @Binds
    @IntoSet
    abstract fun bindCoolReaderProgressSource(impl: CoolReaderProgressSource): ReadingProgressSource

    /**
     * PocketBook: прогресс из тегов `doc.read_progress` его базы `/sdcard/.pocketbook/books.db`.
     *
     * Как и CoolReader, нужен доступ ко всей памяти: база лежит в скрытой папке вне библиотеки.
     */
    @Binds
    @IntoSet
    abstract fun bindPocketBookProgressSource(impl: PocketBookProgressSource): ReadingProgressSource
}
