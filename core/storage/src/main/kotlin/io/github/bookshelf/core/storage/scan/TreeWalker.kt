package io.github.bookshelf.core.storage.scan

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.storage.saf.SourceRef

/**
 * Обход дерева источника.
 *
 * Реализация обязана быть прерываемой и возобновляемой: обход идёт в ширину порциями,
 * периодически отдаёт накопленный батч в [onBatch] и возвращает [ScanCursor] для продолжения.
 * `null` в успешном результате означает «обход завершён»; непустой курсор — «продолжить
 * с сохранённого места» (docs/architecture.md §4.5).
 */
interface TreeWalker {

    /**
     * @param cursor точка продолжения или `null` для обхода с начала;
     * @param onBatch вызывается на каждом батче найденных записей (50–100 файлов);
     * @return курсор для продолжения либо `null`, если дерево обойдено целиком.
     */
    suspend fun walk(
        source: SourceRef,
        cursor: ScanCursor?,
        options: ScanOptions,
        onBatch: suspend (List<DocumentEntry>) -> Unit,
    ): AppResult<ScanCursor?>
}

/**
 * Реализация обхода по умолчанию.
 *
 * Сознательно без состояния: точка продолжения целиком лежит в [ScanCursor], поэтому
 * перезапуск после `Service.onTimeout` или убийства процесса не теряет прогресс.
 * Зависимости (`SafTreeReader`, диспетчеры, часы) добавляются в Sprint 2 вместе с реализацией.
 */
class DefaultTreeWalker : TreeWalker {

    override suspend fun walk(
        source: SourceRef,
        cursor: ScanCursor?,
        options: ScanOptions,
        onBatch: suspend (List<DocumentEntry>) -> Unit,
    ): AppResult<ScanCursor?> {
        // STUB(Sprint 2): BFS через SafTreeReader.children, лимиты maxDepth/расширений/glob,
        // батчи по 50–100 записей, проверка coroutineContext.isActive между папками.
        TODO("STUB(Sprint 2): обход в ширину с сохранением курсора после каждого батча")
    }
}
