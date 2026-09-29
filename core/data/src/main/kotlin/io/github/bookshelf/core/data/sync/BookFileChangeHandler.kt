package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.datastore.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Что делать с данными книги, когда её файл изменился.
 *
 * Скан видит изменение по размеру и времени файла (читать 2000 файлов ради проверки было бы
 * слишком дорого) и обновляет запись книги. Дальше возможны два поведения, и выбор за человеком:
 *
 * * `advanced.keepMetadataOnFileChange` **включено** (умолчание) — книга та же: оценка, избранное,
 *   статус чтения, прогресс и полки остаются;
 * * **выключено** — файл заменили, значит это другая книга, и данные прежней стираются.
 *
 * Отдельным классом от скана: скан — это обход дерева и работа с SAF, а это решение о данных
 * человека, и проверять его надо без дерева документов.
 */
@Singleton
class BookFileChangeHandler @Inject constructor(
    private val settings: SettingsRepository,
    private val cleaner: BookUserDataCleaner,
) {

    /**
     * Обработать изменение файла книги.
     *
     * Вызывается **после** того, как скан записал обновлённые факты файла: иначе запись с прежней
     * оценкой перезаписала бы уже очищенные данные.
     */
    suspend fun onFileChanged(bookId: String): AppResult<Unit> {
        val keep = settings.current().advanced.keepMetadataOnFileChange
        if (keep) return AppResult.success(Unit)
        return cleaner.clear(bookId)
    }
}
