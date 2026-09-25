package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Удаление записей о пропавших файлах старше N дней (настройка, по умолчанию 30).
 *
 * Важно: удаляется только запись о книге, файл не трогается — приложение никогда не удаляет
 * пользовательские файлы.
 *
 * STUB(Sprint 9): тело пустое.
 */
@HiltWorker
class MissingCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        /** Книга возвращается на место — история чтения и коллекции сохраняются. */
        const val KEY_OLDER_THAN_DAYS: String = "olderThanDays"
    }
}
