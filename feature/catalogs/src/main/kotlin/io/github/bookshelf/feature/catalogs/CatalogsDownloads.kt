package io.github.bookshelf.feature.catalogs

import io.github.bookshelf.core.data.model.DownloadState
import io.github.bookshelf.core.data.model.OpdsDownload

/**
 * Состояние загрузок в браузере каталога.
 *
 * Чистые функции, а не код внутри Composable: именно они решают, что увидит пользователь в том
 * месте, где стоит, — строку «Загрузка идёт: 45%» под книгой и счётчик у кнопки «Загрузки».
 * Проверяются JUnit без устройства.
 */

/**
 * Состояние загрузки записи для карточки.
 *
 * Берётся активная загрузка, если она есть: у одной книги может остаться и завершённая запись,
 * и новая в очереди (повторное скачивание), а показывать надо то, что происходит сейчас. Порядок
 * списка — свежие сверху (так его отдаёт `opds_downloads.createdAt DESC`), поэтому вторая строка
 * ниже — про последнюю попытку.
 */
fun entryDownloadOf(entryId: String, downloads: List<DownloadUi>): EntryDownloadUi? {
    val forEntry = downloads.filter { download -> download.entryId == entryId }
    if (forEntry.isEmpty()) return null
    val download = forEntry.firstOrNull { it.isActive } ?: forEntry.first()
    return EntryDownloadUi(
        label = entryDownloadLabel(download),
        progressPercent = download.progressPercent,
        isActive = download.isActive,
        isDone = download.state == DownloadState.DONE,
    )
}

/** Подпись состояния загрузки для карточки записи. */
fun entryDownloadLabel(download: DownloadUi): String = when (download.state) {
    DownloadState.QUEUED -> "Загрузка началась: в очереди"
    DownloadState.RUNNING -> "Загрузка идёт: ${download.progressPercent}%"
    DownloadState.PAUSED -> "Загрузка на паузе: ${download.progressPercent}%"
    DownloadState.DONE -> "Скачано"
    DownloadState.FAILED -> "Ошибка загрузки"
    DownloadState.CANCELLED -> "Загрузка отменена"
}

/** Проставить каждой записи фида её состояние загрузки. */
fun withDownloads(
    entries: List<FeedEntryUi>,
    downloads: List<DownloadUi>,
): List<FeedEntryUi> = entries.map { entry ->
    entry.copy(download = entryDownloadOf(entry.id, downloads))
}

/**
 * Подпись кнопки загрузок в шапке фида.
 *
 * Число показывает, сколько загрузок идёт прямо сейчас: без него кнопка не отвечала на вопрос
 * «началась ли загрузка», ради которого она и появилась.
 */
fun downloadsButtonLabel(downloads: List<DownloadUi>): String {
    val active = downloads.count { download -> download.isActive }
    return if (active > 0) "Загрузки ($active)" else "Загрузки"
}

/** Полная шкала прогресса загрузки: [DownloadUi.progressPercent] хранит проценты, 0..100. */
private const val PERCENT_MAX = 100

/**
 * Загрузка для строки очереди.
 *
 * Прогресс считается из скачанных байт: размер известен не всегда, поэтому при `null` показывается
 * 0, а готовая загрузка — как 100%. На паузе загрузка остаётся активной: её ещё можно отменить.
 */
internal fun OpdsDownload.toDownloadUi(): DownloadUi {
    val total = expectedSize
    val percent = when {
        state == DownloadState.DONE -> PERCENT_MAX
        total == null || total <= 0L -> 0
        else -> ((bytesDownloaded.toDouble() / total) * PERCENT_MAX).toInt().coerceIn(0, PERCENT_MAX)
    }
    val isActive = state == DownloadState.QUEUED ||
        state == DownloadState.RUNNING ||
        state == DownloadState.PAUSED
    return DownloadUi(
        id = id,
        entryId = entryId,
        state = state,
        title = title,
        stateLabel = when (state) {
            DownloadState.QUEUED -> "В очереди"
            DownloadState.RUNNING -> "Скачивается"
            DownloadState.PAUSED -> "На паузе"
            DownloadState.DONE -> "Готово"
            DownloadState.FAILED -> "Ошибка"
            DownloadState.CANCELLED -> "Отменено"
        },
        progressPercent = percent,
        isActive = isActive,
        bookId = bookId,
        error = error,
    )
}
