package io.github.bookshelf.rar

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.bookshelf.core.metadata.rar.RarArchive

/**
 * Реализация чтения RAR для flavor `full`.
 *
 * Модуль живёт в flavor-источнике `app/src/full`: в `fdroid` и `play` его нет, а раз нет и
 * источника с junrar, набор реализаций [RarArchive] остаётся пустым — парсер CBR видит это
 * и не предлагает формат, вместо падения или «формат не поддерживается» на живом файле.
 */
@Module
@InstallIn(SingletonComponent::class)
object RarModule {

    @Provides
    @IntoSet
    fun provideRarArchive(): RarArchive = JunrarRarArchive()
}
