package io.github.bookshelf.work

import io.github.bookshelf.core.common.error.DownloadFailure

/**
 * Опознать причину отказа загрузки по тексту нижнего слоя.
 *
 * Слова для человека выбираются кодом ([DownloadFailure]), а сюда приходит диагностика: сырой
 * ответ провайдера, код ответа сервера. Отдельным кодом помечается только отказ в записи — тот
 * случай, который человек может исправить сам (дефект 29.09.2026: в очереди был сырой
 * `Permission Denial`, по которому нельзя понять, что делать). Всё остальное — общий код: подробность
 * остаётся в `opds_downloads.error` и в журнале (docs/tdd.md §7).
 */
internal fun downloadFailureOf(raw: String?): DownloadFailure =
    if (PERMISSION_DENIAL_MARKERS.any { marker -> raw?.contains(marker, ignoreCase = true) == true }) {
        DownloadFailure.NO_WRITE_PERMISSION
    } else {
        DownloadFailure.DOWNLOAD_FAILED
    }

/** Признаки отказа в доступе: так отвечает `ExternalStorageProvider`, когда нет права записи. */
private val PERMISSION_DENIAL_MARKERS = listOf(
    "Permission Denial",
    "MANAGE_DOCUMENTS",
    "grantUriPermission",
)
