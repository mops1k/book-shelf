package io.github.bookshelf.core.readerlink.profile

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище профилей читалок.
 *
 * Профили — JSON-документы (см. [ReaderProfile]), поэтому репозиторий работает с целыми
 * профилями, а не с отдельными полями. Дефолт для формата и последняя использованная читалка
 * хранятся в настройках (DataStore) и не входят в таблицу `reader_profiles`.
 *
 * Реализация появляется в Sprint 5 (docs/roadmap.md, Спринт 5).
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

/** Заглушка репозитория профилей. */
@Singleton
class DefaultReaderProfileRepository @Inject constructor() : ReaderProfileRepository {

    override fun observeEnabled(): Flow<List<ReaderProfile>> =
        TODO("STUB(Sprint 5): читать reader_profiles и отдавать Flow")

    // STUB(Sprint 5): upsert — записать JSON профиля в reader_profiles

    override suspend fun upsert(profile: ReaderProfile) {
        TODO("STUB(Sprint 5): записать JSON профиля в reader_profiles")
    }

    // STUB(Sprint 5): delete — запретить удаление встроенных presets

    override suspend fun delete(id: String) {
        TODO("STUB(Sprint 5): удалить профиль по id, кроме isBuiltIn")
    }

    // STUB(Sprint 5): findByPackage — индекс по packageName

    override suspend fun findByPackage(pkg: String): ReaderProfile? =
        TODO("STUB(Sprint 5): найти профиль по packageName")

    // STUB(Sprint 5): defaultForFormat — читать настройку format_defaults

    override suspend fun defaultForFormat(format: String): ReaderProfile? =
        TODO("STUB(Sprint 5): вернуть читалку по умолчанию для формата")

    // STUB(Sprint 5): setDefaultForFormat — писать настройку format_defaults

    override suspend fun setDefaultForFormat(format: String, profileId: String) {
        TODO("STUB(Sprint 5): сохранить читалку по умолчанию для формата")
    }
}
