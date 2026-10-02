package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
 * `filesDir/datastore/bookshelf_settings.preferences_pb`), который собирается той же фабрикой
 * [createSettingsDataStore], что и в тестах.
 *
 * Разделение ролей: чтение и запись ключей живут в [SettingsCodec], здесь — только ввод-вывод.
 *
 * [update] выполняет чтение и запись **внутри одной транзакции** `edit`. Это не оптимизация, а
 * требование корректности: два параллельных изменения (пользователь на полке и воркер очистки
 * кэша) иначе могли бы переписать друг друга — второй снимок был бы прочитан до записи первого.
 * Наружу приходит только результат [transform].
 *
 * Настройки применяются без перезапуска («План работ», DoD Sprint 5): потребители обязаны
 * подписываться на [settings], а не читать [current] один раз при старте.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            // Битый или недоступный файл не должен ронять приложение: работаем на умолчаниях.
            // Фабрика уже подменила битый файл пустыми настройками, но полностью недоступное
            // хранилище (кончилось место, отозван доступ) приходит сюда отдельным IOException.
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map(SettingsCodec::decode)

    /**
     * Сброс к умолчаниям: служебные поля и зеркало читалки переживают сброс.
     *
     * Почему не `update { AppSettings() }`: это обнулило бы флаг разового бэкфилла жанров (он
     * запускался бы заново при каждом старте) и «последнюю читалку» (приложение забыло бы, чем
     * открывало книгу). Данные (папки, каталоги, книги, загрузки) сброс не затрагивает: они лежат
     * в таблицах, а не в настройках.
     */
    override suspend fun resetToDefaults() {
        update { current ->
            val defaults = AppSettings()
            defaults.copy(
                library = defaults.library.copy(
                    metadataBackfillDone = current.library.metadataBackfillDone,
                ),
                reader = defaults.reader.copy(
                    defaultReaderByFormat = current.reader.defaultReaderByFormat,
                    lastUsedReaderByFormat = current.reader.lastUsedReaderByFormat,
                ),
            )
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            SettingsCodec.encode(preferences, transform(SettingsCodec.decode(preferences)))
        }
    }

    override suspend fun current(): AppSettings = settings.first()
}
