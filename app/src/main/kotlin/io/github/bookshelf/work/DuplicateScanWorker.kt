package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Поиск дубликатов: считает полные хеши и группирует книги.
 *
 * Запускается только вручную из настроек и только по явно выбранным источникам: полный хеш
 * на 10 000 книг — это часы чтения диска, на e-ink с медленной flash-памятью особенно.
 *
 * STUB(Sprint 7): тело пустое.
 */
@HiltWorker
class DuplicateScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()
}
