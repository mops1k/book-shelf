package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
 * @param scope область корутин DataStore; по умолчанию своя, с [SupervisorJob], чтобы падение
 *   одной записи не убивало последующие.
 */
internal fun createSettingsDataStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = produceFile,
)
