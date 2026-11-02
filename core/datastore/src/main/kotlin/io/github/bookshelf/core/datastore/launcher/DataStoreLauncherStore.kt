package io.github.bookshelf.core.datastore.launcher

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bookshelf.core.datastore.di.LauncherDataStore
import io.github.bookshelf.core.datastore.model.LauncherState
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Хранилище состояния лаунчера на DataStore (ADR-015).
 *
 * Один ключ с JSON вместо десятка типизированных: наборы избранных и скрытых пакетов — это списки
 * переменной длины, и ключ на каждый пакет был бы не хранилищем, а свалкой. Формат читается с
 * `ignoreUnknownKeys`: файл от более новой версии приложения не должен ломать лаунчер.
 *
 * Разбор и сериализация защищены: битый JSON даёт умолчания, а не исключение на старте — иначе
 * список приложений открывался бы падением.
 */
@Singleton
class DataStoreLauncherStore @Inject constructor(
    @LauncherDataStore private val dataStore: DataStore<Preferences>,
) : LauncherStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val state: Flow<LauncherState> = dataStore.data
        .catch { throwable ->
            // Файл может пропасть или не читаться; лаунчер обязан открыться на умолчаниях.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> decode(preferences[STATE_KEY]) }

    override suspend fun update(transform: (LauncherState) -> LauncherState) {
        // Чтение и запись в одной транзакции `edit`: два одновременных изменения (запуск
        // приложения и настройка сортировки) иначе переписывали бы друг друга.
        dataStore.edit { preferences ->
            val next = transform(decode(preferences[STATE_KEY]))
            preferences[STATE_KEY] = json.encodeToString(next)
        }
    }

    override suspend fun current(): LauncherState = state.first()

    private fun decode(raw: String?): LauncherState {
        if (raw == null) return LauncherState()
        return runCatching { json.decodeFromString<LauncherState>(raw) }.getOrNull()
            ?: LauncherState()
    }

    private companion object {
        /** Имя ключа читается в `launcher.preferences_pb` глазами — префикс группы не лишний. */
        val STATE_KEY: Preferences.Key<String> = stringPreferencesKey("launcher.state_json")
    }
}
