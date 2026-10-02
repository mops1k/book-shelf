package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Единая фабрика хранилища настроек (ADR-002).
 *
 * Вынесена из DI-модуля, чтобы тесты проверяли ровно ту устойчивость, что и в бою: обработчик
 * битого файла настраивается здесь, и подмена его в тесте ничего бы не доказывала.
 *
 * Зачем `ReplaceFileCorruptionHandler`: `preferences_pb` — обычный файл, который может остаться
 * битым после обрыва записи или после нехватки места. Без обработчика первый же запуск после
 * такого сбоя падал бы намертво, и пользователь не смог бы открыть настройки, чтобы починить
 * файл. Обработчик заменяет файл пустыми настройками — приложение стартует на умолчаниях.
 *
 * @param produceFile файл хранилища; в бою — `context.preferencesDataStoreFile(...)`;
 * @param dispatchers диспетчеры приложения: свой `Dispatchers.IO` в main-коде запрещён
 *   («TDD» §5), а область корутин собирается здесь — с [SupervisorJob], чтобы падение
 *   одной записи не убивало последующие.
 */
internal fun createSettingsDataStore(
    produceFile: () -> File,
    dispatchers: AppDispatchers,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = CoroutineScope(dispatchers.io + SupervisorJob()),
    produceFile = produceFile,
)
