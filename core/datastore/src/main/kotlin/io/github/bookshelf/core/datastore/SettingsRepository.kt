package io.github.bookshelf.core.datastore

import io.github.bookshelf.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Единственный вход к настройкам приложения (ADR-002).
 *
 * Настройки живут в DataStore Preferences, а не в `SharedPreferences`: последний блокирует поток
 * при первом чтении, а на e-ink с медленным хранилищем это заметный кадр. DataStore отдаёт [Flow],
 * поэтому **любое изменение применяется без перезапуска** («План работ», DoD Sprint 5).
 *
 * Контракт намеренно без `suspend fun get(key)`: настройки читаются и пишутся целым снимком
 * [AppSettings], иначе потребители начнут собирать состояние из кусков и разъедется с экспортом
 * настроек в JSON.
 */
interface SettingsRepository {

    /** Текущий снимок настроек; эмитит новое значение при каждом изменении. */
    val settings: Flow<AppSettings>

    /**
     * Изменить настройки атомарно.
     *
     * [transform] получает текущий снимок и возвращает новый — так два параллельных изменения
     * не затирают друг друга (read-modify-write внутри одной транзакции DataStore).
     */
    suspend fun update(transform: (AppSettings) -> AppSettings)

    /** Одноразовое чтение текущего снимка (для кода без подписки: воркеры, миграции). */
    suspend fun current(): AppSettings

    /**
     * Вернуть настройки к документированным умолчаниям ([model.AppSettings] по умолчанию).
     *
     * Сбрасываются только предпочтения. Не сбрасываются:
     * * **служебные поля** — `library.metadataBackfillDone` (флаг разового бэкфилла жанров) и
     *   `reader.lastUsedReaderByFormat` (состояние «последняя читалка»): это не настройки, а
     *   память приложения о проделанной работе, и сброс запускал бы её заново;
     * * **зеркало читалки по умолчанию** `reader.defaultReaderByFormat`: источник истины —
     *   таблица `format_defaults`, а не настройки, поэтому сброс настроек не меняет выбор читалки;
     * * **данные** — папки-источники, каталоги OPDS, библиотека, загрузки и файлы кэша: они лежат
     *   в своих таблицах, и настройки их не касаются.
     */
    suspend fun resetToDefaults()
}
