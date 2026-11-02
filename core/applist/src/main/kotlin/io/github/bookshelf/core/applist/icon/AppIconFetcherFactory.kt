package io.github.bookshelf.core.applist.icon

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.asImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ключ иконки приложения в кэше Coil (ADR-015).
 *
 * `versionCode` входит в ключ намеренно: после обновления приложения его иконка могла измениться,
 * и кэш по одному имени пакета показывал бы старую картинку бесконечно. Это же значение делает
 * ключ `data class` — Coil сравнивает модели по значению.
 */
data class AppIconKey(
    val packageName: String,
    val versionCode: Long,
)

/**
 * Загрузчик иконок приложений для Coil: `PackageManager` → `Drawable` → кадр.
 *
 * Сеть здесь не нужна и не подключается: иконка уже лежит на устройстве. Кэширование делает Coil
 * в памяти (диск не трогаем — иконок немного, а лишний кэш на e-ink ни к чему).
 */
@Singleton
class AppIconFetcherFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : Fetcher.Factory<AppIconKey> {

    override fun create(data: AppIconKey, options: Options, imageLoader: ImageLoader): Fetcher =
        AppIconFetcher(context = context, key = data)
}

/** Читает иконку пакета; если приложение удалили между чтением списка и отрисовкой — `null`. */
private class AppIconFetcher(
    private val context: Context,
    private val key: AppIconKey,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val drawable = runCatching { context.packageManager.getApplicationIcon(key.packageName) }
            .getOrNull()
            ?: return null
        return ImageFetchResult(
            // `asImage()` заворачивает Drawable без копирования пикселей: иконка рисуется как есть.
            image = drawable.asImage(),
            // Иконка уже нужного размера — повторная выборка не нужна.
            isSampled = true,
            dataSource = DataSource.MEMORY,
        )
    }
}
