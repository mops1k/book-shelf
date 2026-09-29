package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.bookshelf.core.data.sync.LibrarySync

/**
 * Фоновая синхронизация библиотеки с файловой системой.
 *
 * Работа периодическая и уникальная (`KEEP`): чаще раза в 15 минут WorkManager всё равно не
 * запустит задачу, а наложений быть не должно — второй проход по тому же дереву ничего не
 * добавит, но потратит батарею и время провайдера SAF.
 *
 * Ничего не решает сама: инкрементальный скан ставит [LibrarySync], а он же используется при
 * возврате в приложение — один путь обновления библиотеки вместо двух.
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val librarySync: LibrarySync,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Ошибки синхронизации не роняют работу: неудачный проход повторит следующее срабатывание,
        // а `Result.retry()` на периодической работе превратился бы в немедленные повторы.
        librarySync.syncNow()
        return Result.success()
    }

    companion object {
        /** Имя уникальной периодической работы. */
        const val WORK_NAME: String = "library-sync"

        /** Тег для наблюдателей и тестов. */
        const val WORK_TAG: String = "library-sync"
    }
}
