package io.github.bookshelf.core.readerlink.profile

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище профилей читалок.
 *
 * Профили — JSON-документы (см. [ReaderProfile]), поэтому репозиторий работает с целыми
 * профилями, а не с отдельными полями. Дефолт для формата и последняя использованная читалка
 * хранятся в настройках (DataStore) и не входят в таблицу `reader_profiles`.
 *
 * Реализация живёт в `:core:data` (`ReaderProfileRepositoryImpl`): таблицы `reader_profiles` и
 * `format_defaults` — достояние `:core:database`, а этот модуль остаётся тонким слоем и с Room
 * не работает.
 */
interface ReaderProfileRepository {

    /** Включённые профили для выбора читалки; порядок — по убыванию [ReaderProfile.priority]. */
    fun observeEnabled(): Flow<List<ReaderProfile>>

    /** Вставить или обновить профиль по [ReaderProfile.id]. */
    suspend fun upsert(profile: ReaderProfile)

    /** Удалить профиль; встроенные presets удалять нельзя (см. [ReaderProfile.isBuiltIn]). */
    suspend fun delete(id: String)

    /** Профиль по пакету — используется при сопоставлении с результатами discovery. */
    suspend fun findByPackage(pkg: String): ReaderProfile?

    /** Читалка по умолчанию для формата (`format_defaults`). */
    suspend fun defaultForFormat(format: String): ReaderProfile?

    /** Назначить читалку по умолчанию для формата. */
    suspend fun setDefaultForFormat(format: String, profileId: String)
}
