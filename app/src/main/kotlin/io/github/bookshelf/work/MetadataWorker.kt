package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Извлечение метаданных и обложек.
 *
 * Требования (docs/architecture.md §4.4): батарея не низкая, ограниченный параллелизм
 * (по умолчанию 2 на e-ink), приоритет книг, видимых на экране, — диапазон приходит
 * через `MetadataQueue.hint`.
 *
 * STUB(Sprint 3): тело пустое, чтобы каркас собирался; зависимости (MetadataQueue,
 * BookRepository) подключаются в Спринте 3.
 */
@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()
}
