package io.github.bookshelf.core.storage.scan

import kotlinx.serialization.Serializable

/**
 * Курсор возобновляемого скана.
 *
 * Сериализуется в JSON и сохраняется в `scan_runs.cursorDocumentId`, поэтому обход обязан
 * переживать `Service.onTimeout`, потерю foreground-режима и убийство процесса: Android 15
 * (API 35) ограничивает `dataSync` суммарно 6 часами за 24 часа, после чего система вызывает
 * `Service.onTimeout`, и сервис обязан остановиться сам (docs/architecture.md §4.5).
 *
 * @param documentId последняя обработанная запись;
 * @param queue папки, которые уже открыты, но ещё не обойдены; глубина хранится у каждой
 * папки отдельно, иначе после продолжения нельзя восстановить границу BFS.
 */
@Serializable
data class ScanCursor(
    val documentId: String,
    val queue: List<Item>,
) {

    /** Папка в очереди обхода с её глубиной относительно корня дерева. */
    @Serializable
    data class Item(
        val documentId: String,
        val depth: Int,
    )
}
