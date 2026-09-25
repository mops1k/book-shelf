package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.bookshelf.core.datastore.model.AppSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Реализация [SettingsRepository] на DataStore Preferences.
 *
 * Конструктор и DI-обвязка боевые: `DataStore<Preferences>` приходит из
 * [io.github.bookshelf.core.datastore.di.DataStoreModule] (единый файл
 * `filesDir/datastore/bookshelf_settings.preferences_pb`).
 *
 * STUB(Sprint 1): сопоставление ключей [SettingsKeys] ↔ [AppSettings] (чтение и запись) ещё не
 *  реализовано. Сейчас:
 *  * [settings] и [current] отдают документированные значения по умолчанию, чтобы UI и воркеры
 *    запускались (важно для DoD Sprint 1 «запускается на API 24 и на реальном e-ink»);
 *  * [update] намеренно падает с `TODO`, а не молча теряет запись: тихая потеря настройки хуже
 *    громкой ошибки на этапе каркаса.
 *
 *  В Sprint 1: чтение каждого ключа с `?: Defaults…`, запись в `dataStore.edit { }`, устойчивость
 *  к битому файлу (`ReplaceFileCorruptionHandler`) и экспорт/импорт настроек в JSON.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            // Битый или недоступный файл не должен ронять приложение: работаем на умолчаниях.
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        // STUB(Sprint 1): ключи SettingsKeys -> AppSettings.
        .map { AppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        TODO("STUB(Sprint 1): dataStore.edit { } — записать все ключи из transform(current())")
    }

    override suspend fun current(): AppSettings = settings.first()
}
