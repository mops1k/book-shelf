package io.github.bookshelf.core.datastore.update

/** Имена файла и ключа хранилища проверок обновлений (ADR-018). */
object UpdateKeys {

    /** Файл DataStore (`filesDir/datastore/bookshelf_update.preferences_pb`). */
    const val FILE_NAME: String = "bookshelf_update"

    /** Ключ времени последней проверки. */
    const val LAST_CHECK_EPOCH_MS: String = "update.last_check_epoch_ms"
}
