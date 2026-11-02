package io.github.bookshelf.core.update.download

import java.io.File

/**
 * Состояние скачивания APK обновления (ADR-018, фаза 3).
 *
 * Отдельно «скачиваю» с прогрессом и «готово»: на e-ink нет анимаций, и единственный признак
 * жизни — счётчик процентов, который обновляется по мере загрузки.
 */
sealed interface UpdateDownloadState {

    /** Скачивание не запущено. */
    data object Idle : UpdateDownloadState

    /** Скачивается; [percent] — 0…100, `null` если размер неизвестен. */
    data class Running(val percent: Int?) : UpdateDownloadState

    /** Скачано: файл готов к передаче установщику. */
    data class Done(val file: File) : UpdateDownloadState

    /** Скачивание не удалось; [reason] — короткая причина для интерфейса. */
    data class Failed(val reason: String) : UpdateDownloadState
}
