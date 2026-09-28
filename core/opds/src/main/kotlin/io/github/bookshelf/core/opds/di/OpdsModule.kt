package io.github.bookshelf.core.opds.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.opds.auth.CredentialStore
import io.github.bookshelf.core.opds.auth.DefaultCredentialStore
import io.github.bookshelf.core.opds.cache.DefaultOpdsFeedCache
import io.github.bookshelf.core.opds.cache.OpdsFeedCache
import io.github.bookshelf.core.opds.client.DohResolver
import io.github.bookshelf.core.opds.client.HostResolver
import io.github.bookshelf.core.opds.client.OkHttpOpdsClient
import io.github.bookshelf.core.opds.client.OpdsHttpClient
import io.github.bookshelf.core.opds.download.DefaultOpdsDownloader
import io.github.bookshelf.core.opds.download.OpdsDownloader
import io.github.bookshelf.core.opds.parser.Opds1XmlParser
import io.github.bookshelf.core.opds.parser.OpdsFeedParser
import javax.inject.Singleton

/**
 * DI-модуль OPDS.
 *
 * Модуль отсутствует в «чистой офлайн»-сборке: `:core:opds` — единственное место с доступом
 * в сеть, а разрешение `INTERNET` объявляется только в манифесте flavor'а с OPDS
 * (docs/opds.md §4.1, ADR-006). Поэтому ни один офлайн-модуль не должен зависеть от `:core:opds`.
 *
 * Парсером по умолчанию связан [Opds1XmlParser] — обязательный OPDS 1.2. `Opds2JsonParser`
 * намеренно не связан: он включается флагом в Sprint 12, и тогда выбор парсера станет
 * осознанным (фабрика/делегат), а не ещё одной привязкой в этом модуле.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OpdsModule {

    @Binds
    abstract fun bindOpdsHttpClient(
        impl: OkHttpOpdsClient,
    ): OpdsHttpClient

    @Binds
    abstract fun bindOpdsFeedParser(
        impl: Opds1XmlParser,
    ): OpdsFeedParser

    @Binds
    abstract fun bindOpdsFeedCache(
        impl: DefaultOpdsFeedCache,
    ): OpdsFeedCache

    @Binds
    abstract fun bindOpdsDownloader(
        impl: DefaultOpdsDownloader,
    ): OpdsDownloader

    @Binds
    abstract fun bindCredentialStore(
        impl: DefaultCredentialStore,
    ): CredentialStore

    /**
     * Резолвер имён для каталогов: системный DNS, а при его отказе — DoH.
     *
     * Отдельной привязкой, а не внутри клиента: так клиент можно проверить подставным резолвером,
     * а политика резолвинга остаётся в одном месте (docs/opds.md). В companion — потому что модуль
     * абстрактный (`@Binds`), а Dagger запрещает смешивать в одном модуле абстрактные привязки
     * и обычные `@Provides`.
     */
    companion object {

        @Provides
        @Singleton
        fun provideHostResolver(): HostResolver = DohResolver(DohResolver.defaultHttpClient())
    }
}
