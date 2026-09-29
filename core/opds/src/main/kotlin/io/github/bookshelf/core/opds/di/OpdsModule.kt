package io.github.bookshelf.core.opds.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.opds.auth.AndroidKeystoreCipher
import io.github.bookshelf.core.opds.auth.CredentialStore
import io.github.bookshelf.core.opds.auth.DefaultCredentialStore
import io.github.bookshelf.core.opds.auth.SecretCipher
import io.github.bookshelf.core.opds.cache.DefaultOpdsFeedCache
import io.github.bookshelf.core.opds.cache.OpdsFeedCache
import io.github.bookshelf.core.opds.client.DohResolver
import io.github.bookshelf.core.opds.client.HostResolver
import io.github.bookshelf.core.opds.client.OkHttpOpdsClient
import io.github.bookshelf.core.opds.client.OpdsHttpClient
import io.github.bookshelf.core.opds.download.DefaultOpdsDownloader
import io.github.bookshelf.core.opds.download.OpdsDownloader
import io.github.bookshelf.core.opds.parser.OpdsFeedParser
import io.github.bookshelf.core.opds.parser.OpdsFeedRouter
import javax.inject.Singleton

/**
 * DI-модуль OPDS.
 *
 * Модуль отсутствует в «чистой офлайн»-сборке: `:core:opds` — единственное место с доступом
 * в сеть, а разрешение `INTERNET` объявляется только в манифесте flavor'а с OPDS
 * (docs/opds.md §4.1, ADR-006). Поэтому ни один офлайн-модуль не должен зависеть от `:core:opds`.
 *
 * Парсером связан [OpdsFeedRouter]: он сам выбирает разбор по телу ответа — OPDS 1.2 (Atom XML)
 * или OPDS 2.0 (JSON). Так каталоги обеих версий открываются одинаково, и добавление версии не
 * требует правок в слое данных.
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
        impl: OpdsFeedRouter,
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

    @Binds
    abstract fun bindSecretCipher(
        impl: AndroidKeystoreCipher,
    ): SecretCipher


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

        /**
         * Файл настроек с секретами каталогов.
         *
         * Отдельный файл, а не общие настройки: так секреты видно при разборе, и их случайно не
         * выгрузит экспорт настроек.
         */
        @Provides
        @Singleton
        fun provideOpdsSecrets(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(DefaultCredentialStore.PREFS_NAME, Context.MODE_PRIVATE)
    }
}
