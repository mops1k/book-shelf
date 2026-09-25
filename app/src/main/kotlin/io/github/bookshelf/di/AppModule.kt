package io.github.bookshelf.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.di.ApplicationScope
import io.github.bookshelf.core.common.di.CoverCacheDir
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
}
