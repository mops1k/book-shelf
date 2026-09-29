package io.github.bookshelf

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import io.github.bookshelf.work.LibrarySyncScheduler
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * Application-класс.
 *
 * Ответственности: собрать DI-граф, отдать WorkManager нашу фабрику воркеров (иначе
 * `@HiltWorker`-воркеры сканирования не получат зависимости) и настроить загрузчик обложек.
 *
 * Загрузчик настраивается здесь, потому что Coil по умолчанию умеет только локальные файлы:
 * сетевого загрузчика в `coil-core`/`coil-compose` нет вовсе. Без него `AsyncImage(coverUrl)`
 * в карточке книги молча не рисовал ничего — обложка записи OPDS приходит ссылкой, а не файлом
 * в кэше, и пользователь видел пустой прямоугольник.
 */
@HiltAndroidApp
class BookShelfApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Тот же HTTP-клиент, что и для обложек: с DoH-резолвером, как у каталогов. */
    @Inject
    lateinit var coverHttpClient: OkHttpClient

    /** Расписание фоновой синхронизации библиотеки с файловой системой. */
    @Inject
    lateinit var librarySyncScheduler: LibrarySyncScheduler

    override fun onCreate() {
        super.onCreate()
        // Фоновая синхронизация ставится один раз на установку: новые и удалённые на устройстве
        // книги подхватываются без входа в приложение (сам вход синхронизирует отдельно).
        librarySyncScheduler.schedulePeriodic()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { coverHttpClient }))
        }
        .build()
}
