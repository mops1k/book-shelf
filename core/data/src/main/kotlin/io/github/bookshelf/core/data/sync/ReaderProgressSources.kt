package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId

/**
 * Какие источники прогресса включил человек.
 *
 * Связь настроек с обходом живёт здесь, а не в настройках и не в источниках: `:core:datastore` не
 * знает про читалки, а `:core:readerlink` — про настройки, и разрывать эту границу ради одной
 * функции не стоит.
 *
 * Значение по умолчанию у каждой интеграции — выключено: прогресс читается из чужих файлов на карте
 * и из чужого сервиса, и включать это без ведома человека нельзя (решение по FBReader, 29.09.2026).
 */
fun ReaderSettings.enabledProgressSources(): Set<ProgressSourceId> = buildSet {
    if (enableFbReaderProgress) add(ProgressSourceId.FBREADER_AIDL)
    if (enableKoReaderProgress) add(ProgressSourceId.KOREADER_SIDECAR)
    if (enableCoolReaderProgress) add(ProgressSourceId.COOLREADER_DB)
    if (enablePocketBookProgress) add(ProgressSourceId.POCKETBOOK_DB)
    if (enableLibreraProgress) add(ProgressSourceId.LIBRERA_JSON)
    if (enableXReaderProgress) add(ProgressSourceId.XREADER_CACHE)
}

/**
 * Идёт ли синхронизация прогресса хотя бы с одной читалкой.
 *
 * По этому признаку ставится и снимается цепочка фоновых работ: до появления второй читалки
 * достаточно было смотреть на флаг FBReader, а теперь выключение одной интеграции не должно
 * останавливать другую.
 */
fun ReaderSettings.isProgressSyncEnabled(): Boolean = enabledProgressSources().isNotEmpty()
