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
import io.github.bookshelf.core.common.di.ApplicationScope
import io.github.bookshelf.core.common.di.ExportDir
import io.github.bookshelf.core.common.di.TempDir
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.data.sync.DownloadScheduler
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.megabytesToBytes
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.files.ContentHasher
import io.github.bookshelf.core.storage.files.DefaultContentHasher
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    /**
     * Монитор съёмных носителей.
     *
     * Читает тома системы и слушает события медиа: на них строится появление и исчезновение полки
     * «Карта памяти» (требование Александра 01.10.2026).
     */
    @Provides
    @Singleton
    fun provideRemovableVolumeMonitor(
        @ApplicationContext context: Context,
    ): io.github.bookshelf.core.storage.volume.RemovableVolumeMonitor =
        io.github.bookshelf.core.storage.volume.AndroidRemovableVolumeMonitor(context)

    /**
     * Доступ ко всем файлам.
     *
     * API 30+ — `MANAGE_EXTERNAL_STORAGE`; на 24–29 ему соответствует разрешение на чтение памяти.
     * От ответа зависит, спросят ли человека о доступе к носителю (решение «сначала прямой путь»).
     */
    @Provides
    fun provideAllFilesAccess(
        @ApplicationContext context: Context,
    ): io.github.bookshelf.core.common.permission.AllFilesAccess =
        io.github.bookshelf.core.common.permission.AllFilesAccess {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }

        /**
     * Полка съёмного носителя: интерфейс связывается с реализацией здесь.
     *
     * `@Provides`, а не `@Binds`: модуль объектный, абстрактных методов в нём нет — так же связаны
     * остальные порты этого модуля.
     */
    @Provides
    @Singleton
    fun provideRemovableShelfRepository(
        implementation: io.github.bookshelf.core.data.removable.RemovableShelfRepositoryImpl,
    ): io.github.bookshelf.core.data.removable.RemovableShelfRepository = implementation

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

    /**
     * Полный хеш содержимого файла книги.
     *
     * Нужен поиску дублей: он читает файлы целиком, поэтому вызывается только по команде
     * пользователя, а не при каждом скане.
     */
    @Provides
    @Singleton
    fun provideContentHasher(opener: BookContentOpener): ContentHasher = DefaultContentHasher(opener)

    /**
     * Доступ к содержимому книги.
     *
     * Лимит временной копии приходит из настроек (`library.tempCopyLimitMb`): `:core:storage` —
     * низкоуровневый модуль о файлах и SAF, и читать в нём настройки значило бы тянуть в него
     * `:core:datastore`. Связывает их `:app` — единственное место, которое знает оба модуля.
     *
     * Снимок намеренно «живой»: опенер один на процесс, а его API синхронный (без suspend),
     * поэтому значение обновляется подпиской, а читается прямо в момент копирования — смена
     * настройки применяется без перезапуска (docs/roadmap.md, DoD Sprint 5).
     */
    @Provides
    @Singleton
    fun provideBookContentOpener(
        @ApplicationContext context: Context,
        @TempDir tempDir: File,
        settingsRepository: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
        @ExportDir exportDir: File,
    ): BookContentOpener {
        // `SharingStarted.Eagerly` обязателен: у StateFlow нет подписчиков (его значение читает
        // только лямбда ниже), и с `WhileSubscribed` поток настроек не собирался бы вообще.
        val limitBytes = settingsRepository.settings
            .map { megabytesToBytes(it.library.tempCopyLimitMb) }
            // Недоступные настройки роняли бы поток в `@ApplicationScope` — там нет обработчика,
            // и падение корутины унесло бы приложение. Остаёмся на прежней константе, как и до
            // появления настройки: это же значение равно умолчанию `tempCopyLimitMb` (100 МБ).
            .catch { emit(DefaultBookContentOpener.DEFAULT_MAX_TEMP_COPY_BYTES) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DefaultBookContentOpener.DEFAULT_MAX_TEMP_COPY_BYTES,
            )
        return DefaultBookContentOpener(
            resolver = context.contentResolver,
            importDir = File(tempDir, IMPORT_DIR),
            // Каталог экспорта тот же, что открыт через FileProvider (`cacheDir/export`):
            // именно его читалки видят снаружи, а каталог временных копий наружу не отдаётся.
            exportDir = exportDir,
            tempCopyLimitBytes = { limitBytes.value },
        )
    }

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
