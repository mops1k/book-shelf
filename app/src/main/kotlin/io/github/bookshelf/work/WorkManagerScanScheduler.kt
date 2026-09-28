package io.github.bookshelf.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.data.sync.ScanScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ставка сканирования в очередь WorkManager (docs/architecture.md §4.5, ADR-010).
 *
 * Работа **уникальна на источник** с политикой `KEEP`, и это не оптимизация:
 *
 * * два параллельных скана одного дерева писали бы в `books` одновременно и портили прогресс
 *   друг другу — а ещё удваивали бы нагрузку на провайдер SAF;
 * * повторное нажатие «Сканировать» при уже идущем скане не должно ни начинать новый прогон,
 *   ни отменять текущий. `KEEP` оставляет идущую работу, а `REPLACE` — обрезал бы её на
 *   середине, и возобновлять пришлось бы с курсора, который мы только что потеряли.
 */
@Singleton
class WorkManagerScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScanScheduler, MetadataScheduler {

    override suspend fun enqueueScan(sourceId: String) {
        val request = OneTimeWorkRequestBuilder<ScanSourceWorker>()
            .setInputData(workDataOf(ScanSourceWorker.KEY_SOURCE_ID to sourceId))
            .addTag(ScanSourceWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ScanSourceWorker.uniqueWorkName(sourceId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Ставит разбор одной книги.
     *
     * Работа отдельная на книгу, а не пачка на библиотеку: у каждой книги свой приоритет, и
     * видимая на полке должна разобраться раньше той, что лежит в конце списка.
     */
    override suspend fun enqueueMetadata(bookId: String, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<MetadataWorker>()
            .setInputData(
                workDataOf(
                    MetadataWorker.KEY_BOOK_ID to bookId,
                    MetadataWorker.KEY_FORCE to force,
                ),
            )
            .addTag(MetadataWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            metadataWorkName(bookId, force),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun cancelScan(sourceId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(ScanSourceWorker.uniqueWorkName(sourceId))
    }

    /** Имя работы: у принудительного разбора своё, чтобы не столкнуться с обычным (KEEP). */
    private fun metadataWorkName(bookId: String, force: Boolean = false): String =
        if (force) "metadata-force-$bookId" else "metadata-$bookId"
}
