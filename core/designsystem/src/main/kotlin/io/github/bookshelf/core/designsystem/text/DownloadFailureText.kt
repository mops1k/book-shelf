package io.github.bookshelf.core.designsystem.text

import io.github.bookshelf.core.common.error.DownloadFailure
import io.github.bookshelf.core.designsystem.R

/**
 * Слова для отказа загрузки по коду.
 *
 * Рядом с [AppError.text] и [ParseFailure.text]: причина хранится кодом, переводится на экране, а
 * подробности остаются в журнале (docs/tdd.md §7–8).
 */
fun DownloadFailure.text(): AppText = when (this) {
    DownloadFailure.NO_WRITE_PERMISSION -> AppText(R.string.download_failure_no_write_permission)
    DownloadFailure.RECEIVER_FOLDER_GONE -> AppText(R.string.download_failure_receiver_folder_gone)
    DownloadFailure.CREDENTIALS_UNREADABLE -> AppText(R.string.download_failure_credentials_unreadable)
    DownloadFailure.BAD_DOCUMENT_URI -> AppText(R.string.download_failure_bad_document_uri)
    DownloadFailure.DOWNLOAD_FAILED -> AppText(R.string.download_failure_failed)
    DownloadFailure.UNKNOWN -> AppText(R.string.download_failure_unknown)
}
