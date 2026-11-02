package io.github.bookshelf.core.update.download

import kotlinx.coroutines.flow.Flow

/**
 * Порт скачивания APK обновления (ADR-018).
 *
 * Интерфейс, а не класс: ViewModel проверяется юнит-тестами с фейком, а боевая реализация
 * на `DownloadManager` живёт в [AndroidUpdateDownloader].
 */
interface UpdateDownloader {

    /** Скачать [url] под именем [assetName]; поток заканчивается `Done` или `Failed`. */
    fun download(url: String, assetName: String): Flow<UpdateDownloadState>
}
