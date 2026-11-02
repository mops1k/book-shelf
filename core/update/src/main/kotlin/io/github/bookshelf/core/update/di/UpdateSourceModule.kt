package io.github.bookshelf.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.update.download.AndroidUpdateDownloader
import io.github.bookshelf.core.update.download.UpdateDownloader
import io.github.bookshelf.core.update.install.AndroidApkInstaller
import io.github.bookshelf.core.update.install.ApkInstaller
import io.github.bookshelf.core.update.source.GitHubReleaseSource
import io.github.bookshelf.core.update.source.ReleaseSource

/**
 * Источник обновлений: боевой GitHub за портом (ADR-018).
 *
 * Проверка обновлений зависит от [ReleaseSource], а не от GitHub напрямую — в тестах порт
 * подменяется фейком, и ни один тест не ходит в сеть.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateSourceModule {

    @Binds
    abstract fun bindReleaseSource(impl: GitHubReleaseSource): ReleaseSource

    /** Скачивание APK: системный DownloadManager за портом (фаза 3 ADR-018). */
    @Binds
    abstract fun bindUpdateDownloader(impl: AndroidUpdateDownloader): UpdateDownloader

    /** Передача APK установщику: FileProvider + системный диалог за портом. */
    @Binds
    abstract fun bindApkInstaller(impl: AndroidApkInstaller): ApkInstaller
}
