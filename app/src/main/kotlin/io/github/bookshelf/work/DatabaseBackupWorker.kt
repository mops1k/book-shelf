package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.data.backup.DatabaseBackup

/**
 * Ежедневная копия базы в папку библиотеки.
 *
 * База живёт в приватном каталоге приложения и пропадает вместе с ним; копия в папке библиотеки
 * переживает переустановку. Запуск по расписанию подчиняется настройке `advanced.dbBackupEnabled` —
 * её проверяет сам бэкап, поэтому воркер просто сообщает, что запуск автоматический.
 *
 * Ошибки не роняют работу: недоступная папка или занятый файл — обычная жизнь, следующая попытка
 * будет через сутки, а причина остаётся в журнале.
 */
@HiltWorker
class DatabaseBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val logger: AppLogger,
    private val backup: DatabaseBackup,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = backup.backupNow(automatic = true)
        logger.i(TAG, "бэкап базы: ${outcome.getOrNull() ?: outcome.errorOrNull()}")
        return Result.success()
    }

    companion object {
        private const val TAG = "BookShelf"

        const val WORK_NAME = "database-backup"
    }
}
