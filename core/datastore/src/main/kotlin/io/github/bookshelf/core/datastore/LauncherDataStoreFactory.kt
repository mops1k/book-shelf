package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Фабрика хранилища состояния лаунчера (ADR-015).
 *
 * Отдельный файл, а не общий с настройками: битый JSON лаунчера не должен уносить тему, плотность
 * и папки-источники. Обработчик битого файла тот же, что у настроек: после обрыва записи
 * приложение стартует на умолчаниях, а не падает намертво.
 */
internal fun createLauncherDataStore(
    produceFile: () -> File,
    dispatchers: AppDispatchers,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = CoroutineScope(dispatchers.io + SupervisorJob()),
    produceFile = produceFile,
)
