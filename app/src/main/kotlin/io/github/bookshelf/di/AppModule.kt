package io.github.bookshelf.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.di.ApplicationScope
import io.github.bookshelf.core.data.sync.ProgressSyncSchedule
import io.github.bookshelf.work.ReadingProgressSyncScheduler
import io.github.bookshelf.core.common.di.CoverCacheDir
import io.github.bookshelf.core.common.di.OpdsCacheDir
import io.github.bookshelf.core.common.di.DefaultDispatcher
import io.github.bookshelf.core.common.di.ExportDir
import io.github.bookshelf.core.common.di.TempDir
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

/**
 * Зависимости уровня приложения: каталоги и общий scope.
 *
 * Каталоги создаются здесь, а не в местах использования: на e-ink с медленным хранилищем
 * проверка существования папки на каждое обращение — лишние миллисекунды в кадре.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Расписание синхронизации прогресса чтения.
     *
     * Реализация живёт в `:app`, потому что завязана на WorkManager, а порт объявлен в `:core:data`:
     * экран настроек (feature-модуль) не может зависеть от приложения, но обязан уметь сказать
     * «расписание изменилось».
     */
    @Provides
    @Singleton
    fun provideProgressSyncSchedule(scheduler: ReadingProgressSyncScheduler): ProgressSyncSchedule =
        scheduler

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    /** filesDir/covers — единственный крупный потребитель диска (docs/architecture.md §5). */
    @Provides
    @Singleton
    @CoverCacheDir
    fun provideCoverCacheDir(@ApplicationContext context: Context): File =
        File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }

    /** cacheDir/import — временные копии книг для парсеров, которым нужен seek. */
    @Provides
    @Singleton
    @TempDir
    fun provideTempDir(@ApplicationContext context: Context): File =
        File(context.cacheDir, "import").apply { if (!exists()) mkdirs() }

    /** cacheDir/export — копии для читалок через FileProvider. */
    @Provides
    @Singleton
    @ExportDir
    fun provideExportDir(@ApplicationContext context: Context): File =
        File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }

    /**
     * Каталог тел фидов OPDS.
     *
     * Отдельный от обложек намеренно: чистка обложек не должна сносить кэш каталогов, а
     * очистка каталогов — обложки. Внутри `cacheDir`, чтобы ОС убрала его сама.
     */
    @Provides
    @Singleton
    @OpdsCacheDir
    fun provideOpdsCacheDir(@ApplicationContext context: Context): File =
        File(context.cacheDir, OPDS_CACHE_DIR).apply { mkdirs() }

    private const val OPDS_CACHE_DIR = "opds"
}
