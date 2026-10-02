package io.github.bookshelf.core.metadata.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.bookshelf.core.common.flags.FeatureFlags
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.impl.CbrParser
import io.github.bookshelf.core.metadata.parser.impl.CbzParser
import io.github.bookshelf.core.metadata.parser.impl.DocxParser
import io.github.bookshelf.core.metadata.parser.impl.EpubParser
import io.github.bookshelf.core.metadata.parser.impl.Fb2Parser
import io.github.bookshelf.core.metadata.parser.impl.MobiParser
import io.github.bookshelf.core.metadata.parser.impl.OdtParser
import io.github.bookshelf.core.metadata.parser.impl.PdfParser
import io.github.bookshelf.core.metadata.parser.impl.RtfParser
import io.github.bookshelf.core.metadata.parser.impl.TxtParser
import io.github.bookshelf.core.metadata.rar.RarArchive
import io.github.bookshelf.core.metadata.registry.DefaultParserRegistry
import io.github.bookshelf.core.metadata.registry.ParserRegistry
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

/**
 * Связывание парсеров и реестра. Логики здесь нет — только DI-граф.
 *
 * Все парсеры собираются в `Set<BookMetadataParser>` через `@IntoSet`, поэтому добавление
 * нового формата не требует правок реестра: достаточно нового `@Provides`.
 */
@Module
@InstallIn(SingletonComponent::class)
object MetadataModule {

    @Provides
    @IntoSet
    fun provideFb2Parser(): BookMetadataParser = Fb2Parser()

    @Provides
    @IntoSet
    fun provideEpubParser(): BookMetadataParser = EpubParser()

    @Provides
    @IntoSet
    fun providePdfParser(): BookMetadataParser = PdfParser()

    @Provides
    @IntoSet
    fun provideTxtParser(): BookMetadataParser = TxtParser()

    @Provides
    @IntoSet
    fun provideMobiParser(): BookMetadataParser = MobiParser()

    @Provides
    @IntoSet
    fun provideCbzParser(): BookMetadataParser = CbzParser()

    /**
     * CBR доступен только во flavor `full` (junrar под UnRar License, ADR-007).
     *
     * Парсер всегда в графе, но `supports()` возвращает `false`, когда флаг выключен или когда
     * в сборке нет ни одной реализации чтения RAR, — поэтому формат просто не предлагается,
     * без `if (flavor == …)` в логике. Реализацию чтения приносит `:app` из flavor-источника
     * `src/full`, в остальных сборках набор пуст.
     */
    @Provides
    @IntoSet
    fun provideCbrParser(
        featureFlags: FeatureFlags,
        rarReaders: Set<@JvmSuppressWildcards RarArchive>,
    ): BookMetadataParser = CbrParser(featureFlags, rarReaders)

    @Provides
    @IntoSet
    fun provideDocxParser(): BookMetadataParser = DocxParser()

    @Provides
    @IntoSet
    fun provideOdtParser(): BookMetadataParser = OdtParser()

    @Provides
    @IntoSet
    fun provideRtfParser(): BookMetadataParser = RtfParser()

    @Provides
    @Singleton
    fun provideParserRegistry(
        parsers: Set<@JvmSuppressWildcards BookMetadataParser>,
    ): ParserRegistry = DefaultParserRegistry(parsers)
}
