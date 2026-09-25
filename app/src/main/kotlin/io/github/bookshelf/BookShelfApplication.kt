package io.github.bookshelf

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application-класс.
 *
 * Единственная ответственность — собрать DI-граф и отдать WorkManager нашу фабрику воркеров
 * (иначе `@HiltWorker`-воркеры сканирования не получат зависимости).
 */
@HiltAndroidApp
class BookShelfApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
}
