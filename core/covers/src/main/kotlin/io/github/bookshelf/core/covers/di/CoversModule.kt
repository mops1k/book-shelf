package io.github.bookshelf.core.covers.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.covers.cache.CoverCacheCleaner
import io.github.bookshelf.core.covers.cache.DiskCoverCache
import io.github.bookshelf.core.covers.decode.AndroidCoverDecoder
import io.github.bookshelf.core.covers.decode.CoverDecoder

/**
 * DI-обвязка кэша обложек (docs/architecture.md §5).
 *
 * Привязки только интерфейс → реализация: конкретные классы (`DiskCoverCache`, `AndroidCoverDecoder`)
 * уже помечены `@Singleton` и имеют `@Inject`-конструкторы.
 *
 * Каталог кэша (`@CoverCacheDir File`) здесь **не** создаётся: его отдаёт `:app` (`AppModule`),
 * потому что каталогов уровня приложения несколько (`covers`, `import`, `export`) и все они должны
 * создаваться в одном месте. `:core:covers` только потребляет квалификатор из `:core:common`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoversModule {

    @Binds
    abstract fun bindCoverCache(impl: DiskCoverCache): CoverCache

    @Binds
    abstract fun bindCoverCacheCleaner(impl: DiskCoverCache): CoverCacheCleaner

    @Binds
    abstract fun bindCoverDecoder(impl: AndroidCoverDecoder): CoverDecoder
}
