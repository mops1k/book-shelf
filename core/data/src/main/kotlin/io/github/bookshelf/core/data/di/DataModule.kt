package io.github.bookshelf.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.data.repository.MetadataOverrideRepository
import io.github.bookshelf.core.data.repository.OpdsRepository
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.ReadingProgressRepository
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.SearchRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.CollectionRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.FilterRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.MetadataOverrideRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.OpdsRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.ReaderRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.ReadingProgressRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.ScanRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.SearchRepositoryImpl
import io.github.bookshelf.core.data.repository.impl.SourceRepositoryImpl

/**
 * DI-обвязка слоя данных: интерфейс → заглушка-реализация.
 *
 * Feature-модули зависят только от `:core:data` и видят только интерфейсы; конкретные классы
 * скрыты и подменяются по мере спринтов без изменения сигнатур. Все реализации помечены
 * `@Singleton` и создаются Dagger'ом, поэтому здесь нет ни `@Provides`, ни ручной сборки.
 *
 * `MetadataQueue` и `ScanCoordinator` (пакет `sync`) пока не привязаны: их реализации появятся в
 * Sprint 2 вместе с воркерами, а до этого не имеют смысла. Отсутствие заглушки здесь — намеренное:
 * пустой интерфейс в графе хуже его отсутствия.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun bindSourceRepository(impl: SourceRepositoryImpl): SourceRepository

    @Binds
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository

    @Binds
    abstract fun bindFilterRepository(impl: FilterRepositoryImpl): FilterRepository

    @Binds
    abstract fun bindCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    abstract fun bindReaderRepository(impl: ReaderRepositoryImpl): ReaderRepository

    @Binds
    abstract fun bindReadingProgressRepository(impl: ReadingProgressRepositoryImpl): ReadingProgressRepository

    @Binds
    abstract fun bindOpdsRepository(impl: OpdsRepositoryImpl): OpdsRepository

    @Binds
    abstract fun bindMetadataOverrideRepository(impl: MetadataOverrideRepositoryImpl): MetadataOverrideRepository
}
