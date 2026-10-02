package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.database.dao.FormatDefaultDao
import io.github.bookshelf.core.database.dao.ReaderProfileDao
import io.github.bookshelf.core.database.entity.FormatDefaultEntity
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.core.readerlink.profile.ReaderProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Профили читалок в базе: `reader_profiles` и `format_defaults` (Sprint 5, ADR-005).
 *
 * Живёт в `:core:data`, потому что таблицы — достояние `:core:database`, а `:core:readerlink`
 * остаётся тонким слоем: он объявляет интерфейсы и работает с системой, но не с Room.
 *
 * Два правила, ради которых репозиторий вообще существует отдельно от DAO:
 * * **встроенный preset удалить нельзя** — иначе человек снесёт профиль, который приложение
 *   ставит само, и рассыпалась бы привязка «формат → читалка»;
 * * **профиль читается по пакету** — по нему установленная читалка сопоставляется с сохранённой
 *   настройкой (индекс `reader_profiles.packageName`).
 */
@Singleton
class ReaderProfileRepositoryImpl @Inject constructor(
    private val profileDao: ReaderProfileDao,
    private val formatDefaultDao: FormatDefaultDao,
) : ReaderProfileRepository {

    override fun observeEnabled(): Flow<List<ReaderProfile>> =
        profileDao.observeEnabled().map { entities -> entities.map { it.toProfile() } }

    override suspend fun upsert(profile: ReaderProfile) {
        profileDao.upsertAll(listOf(profile.toEntity()))
    }

    override suspend fun delete(id: String) {
        // Встроенный preset не удаляется: молча ничего не делаем — интерфейс возвращает Unit,
        // а решение «это встроенный профиль» уже принято здесь, в одном месте для всех вызовов.
        val stored = profileDao.getById(id) ?: return
        if (stored.isBuiltIn) return
        profileDao.deleteById(id)
    }

    override suspend fun findByPackage(pkg: String): ReaderProfile? =
        profileDao.getByPackage(pkg)?.toProfile()

    override suspend fun defaultForFormat(format: String): ReaderProfile? {
        val stored = formatDefaultDao.getByFormat(format) ?: return null
        // Строка `format_defaults` живёт без внешнего ключа: профиль могли удалить отдельно,
        // и тогда честный ответ — «читалки для формата нет», а не падение.
        return profileDao.getById(stored.profileId)?.toProfile()
    }

    override suspend fun setDefaultForFormat(format: String, profileId: String) {
        formatDefaultDao.upsert(FormatDefaultEntity(format = format, profileId = profileId))
    }
}
