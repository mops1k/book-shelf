package io.github.bookshelf.core.applist.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.applist.catalog.AndroidAppCatalog
import io.github.bookshelf.core.applist.model.AppCatalog
import javax.inject.Singleton

/** Порт каталога приложений → реализация на `PackageManager` (ADR-015). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppListModule {

    @Binds
    @Singleton
    abstract fun bindAppCatalog(catalog: AndroidAppCatalog): AppCatalog
}
