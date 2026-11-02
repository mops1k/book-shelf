package io.github.bookshelf.core.datastore.update

import androidx.datastore.core.DataStore
import io.github.bookshelf.core.datastore.di.UpdateDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Отметка о проверке обновлений в отдельном файле DataStore (ADR-018). */
@Singleton
class DataStoreUpdateStore @Inject constructor(
    // Квалификатор обязателен: без него Dagger отдаёт хранилище настроек, и отметка проверки
    // уезжает в чужой файл, ломая изоляцию (найдено на устройстве 02.11.2026).
    @UpdateDataStore private val dataStore: DataStore<Preferences>,
) : UpdateStore {

    private val lastCheckKey = longPreferencesKey(UpdateKeys.LAST_CHECK_EPOCH_MS)

    override suspend fun lastCheckEpochMs(): Long? = dataStore.data.first()[lastCheckKey]

    override suspend fun markChecked(epochMs: Long) {
        dataStore.edit { preferences -> preferences[lastCheckKey] = epochMs }
    }
}
