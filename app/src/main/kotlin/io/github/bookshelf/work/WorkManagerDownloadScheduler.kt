package io.github.bookshelf.work

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.bookshelf.core.data.sync.DownloadScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запуск загрузки через WorkManager.
 *
 * Работа уникальна на [downloadId] с политикой `KEEP`: повторное нажатие «Скачать» не
 * плодит копии одной и той же загрузки, а перезапуск процесса не теряет уже начатую.
 */
@Singleton
class WorkManagerDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
) : DownloadScheduler {

    override fun schedule(downloadId: String) {
        val request = OneTimeWorkRequestBuilder<OpdsDownloadWorker>()
            .setInputData(Data.Builder().putString(OpdsDownloadWorker.KEY_DOWNLOAD_ID, downloadId).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            WORK_PREFIX + downloadId,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WORK_PREFIX = "opds-download-"
    }
}
