package io.github.bookshelf.di

import android.content.ContentResolver
import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.di.TempDir
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.data.sync.DownloadScheduler
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.files.DefaultBookContentOpener
import io.github.bookshelf.core.storage.saf.AndroidSafTreeReader
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import io.github.bookshelf.core.storage.saf.SafTreeWriterImpl
import io.github.bookshelf.core.storage.scan.DefaultTreeWalker
import io.github.bookshelf.core.storage.scan.TreeWalker
import io.github.bookshelf.work.WorkManagerDownloadScheduler
import io.github.bookshelf.work.WorkManagerScanScheduler
import java.io.File
import javax.inject.Singleton

/**
 * Привязки слоя хранения и сканирования (docs/architecture.md §4).
 *
 * Здесь живут реализации, которым нужен Android: `ContentResolver` и `WorkManager`. В
 * `:core:storage` и `:core:data` их нет намеренно — эти модули обязаны тестироваться без
 * Android-контекста, а обход SAF и постановка работы в очередь на них завязаны по интерфейсам.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSafTreeReader(@ApplicationContext context: Context): SafTreeReader =
        AndroidSafTreeReader(context.contentResolver)

    /**
     * Запись в дерево SAF.
     *
     * Отдельный провайдер рядом с ридером: оба ходят в `ContentResolver`, но разбираются на
     * чтение и запись, и сканеру писать нельзя.
     */
    /**
     * Запуск загрузок OPDS через WorkManager.
     *
     * Отдельный планировщик, а не переиспользование сканирования: у загрузки своя работа,
     * свои ограничения по сети и своя политика уникальности.
     */
    @Provides
    @Singleton
    fun provideDownloadScheduler(
        @ApplicationContext context: Context,
    ): DownloadScheduler = WorkManagerDownloadScheduler(
        WorkManager.getInstance(context),
    )

    @Provides
    @Singleton
    fun provideSafTreeWriter(@ApplicationContext context: Context): SafTreeWriter =
        SafTreeWriterImpl(context)

    /**
     * Обход дерева без состояния: точка продолжения целиком живёт в `ScanCursor`, поэтому
     * перезапуск после остановки процесса ничего не теряет.
     */
    @Provides
    @Singleton
    fun provideTreeWalker(reader: SafTreeReader, dispatchers: AppDispatchers): TreeWalker =
        DefaultTreeWalker(reader, dispatchers)

    @Provides
    @Singleton
    fun provideBookContentOpener(
        @ApplicationContext context: Context,
        @TempDir tempDir: File,
    ): BookContentOpener = DefaultBookContentOpener(
        resolver = context.contentResolver,
        importDir = File(tempDir, IMPORT_DIR),
    )

    /**
     * Ставка в очередь единственной работы на источник.
     *
     * `:core:data` не знает про WorkManager и потому не ставит работу сам — иначе слой данных
     * зависел бы от модуля приложения.
     */
    @Provides
    @Singleton
    fun provideScanScheduler(@ApplicationContext context: Context): ScanScheduler =
        WorkManagerScanScheduler(context)

    @Provides
    @Singleton
    fun provideMetadataScheduler(
        scheduler: WorkManagerScanScheduler,
    ): MetadataScheduler = scheduler

    /** Каталог временных копий книг внутри `cacheDir`. */
    private const val IMPORT_DIR = "import"
}
