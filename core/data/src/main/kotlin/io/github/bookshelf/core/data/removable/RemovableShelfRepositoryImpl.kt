package io.github.bookshelf.core.data.removable

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.permission.AllFilesAccess
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.storage.volume.RemovableVolume
import io.github.bookshelf.core.storage.volume.RemovableVolumeMonitor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.map
import kotlinx.coroutines.flow.first

/**
 * Полка съёмного носителя.
 *
 * Решения, ради которых класс написан именно так:
 * * **источник связывается с носителем по UUID внутри его адреса** ([sourceBelongsToVolume]) — так
 *   не нужны ни новое поле в схеме, ни миграция: адрес дерева тома уже содержит UUID;
 * * **встроенная память носителем не считается** (это решает `removableVolumesOf`), иначе полка
 *   висела бы всегда;
 * * **книги изъятого носителя помечаются пропавшими**: полка библиотеки их не показывает, а данные
 *   (прогресс, полки, оценка) остаются до возврата носителя;
 * * **выключенная настройка убирает полку целиком** — вместе с ней и запросы доступа.
 */
@Singleton
class RemovableShelfRepositoryImpl @Inject constructor(
    private val monitor: RemovableVolumeMonitor,
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val bookDao: BookDao,
    private val sources: SourceRepository,
    private val allFilesAccess: AllFilesAccess,
    private val clock: AppClock,
) : RemovableShelfRepository {

    /**
     * Строка на каждый подключённый том.
     *
     * Носитель может быть разбит на несколько партиций (флешка с двумя разделами — обычное дело,
     * проверено на устройстве 02.10.2026): каждая партиция монтируется своим томом со своим UUID,
     * поэтому у каждой своя строка и свой счёт книг. Показывать только первый том значило бы
     * потерять книги второго раздела.
     */
    override fun observeShelves(): Flow<List<RemovableShelf>> =
        combine(
            monitor.observe(),
            settings.settings,
            sourceDao.observeAll(),
        ) { volumes: List<RemovableVolume>, appSettings, allSources: List<SourceEntity> ->
            if (!appSettings.library.showRemovableShelf) return@combine emptyList()
            volumes.map { volume ->
                val source = allSources.firstOrNull { candidate ->
                    sourceBelongsToVolume(candidate.uri, volume.uuid)
                }
                RemovableShelf(
                    volume = volume,
                    sourceId = source?.id,
                    // Считаются книги источника целиком: пропавшие (изъятый носитель) и так скрыты полкой.
                    bookCount = source?.let { found -> bookDao.countBySource(found.id) } ?: 0,
                    needsAccess = source == null,
                )
            }
        }

    override suspend fun ensureSource(volume: RemovableVolume): AppResult<Unit> {
        if (findSourceOf(volume) != null) return AppResult.success(Unit)
        if (cardAccessOf(allFilesAccess.granted()) == CardAccess.SAF_TREE) {
            // Доступа ко всем файлам нет: спрашивать человека — дело экрана, а не репозитория.
            return AppResult.failure(
                AppError.PermissionDenied(message = "Нужен доступ к съёмному носителю"),
            )
        }
        // Источник заводится по тому пути, по которому том реально читается: на исправной прошивке
        // это `/storage/<uuid>`, на прошивке без публичной точки — `/mnt/media_rw/<uuid>`.
        return sources.addDirectPathSource(
            path = volume.path,
            displayName = displayNameOf(volume),
        ).map { }
    }

    override suspend fun grantTreeAccess(volume: RemovableVolume, treeUri: String): AppResult<Unit> {
        if (!sourceBelongsToVolume(treeUri, volume.uuid)) {
            return AppResult.failure(
                AppError.InvalidArgument(
                    what = "treeUri",
                    message = "Выбранная папка не на этом носителе",
                ),
            )
        }
        // Право берёт `addSafSource`: человек только что выбрал папку, поэтому persistable-доступ
        // система отдаст. Успех заводит источник и ставит его первичный скан — книги появятся сами.
        return sources.addSafSource(uri = treeUri, displayName = displayNameOf(volume)).map { }
    }

    override suspend fun onEjected(volume: RemovableVolume): AppResult<Unit> = appRunCatching {
        val source = findSourceOf(volume) ?: return@appRunCatching
        val ids = bookDao.idsBySource(source.id)
        if (ids.isNotEmpty()) bookDao.markMissing(ids, clock.nowMillis())
    }

    /** Источник, связанный с носителем, — по UUID внутри его адреса. */
    private suspend fun findSourceOf(volume: RemovableVolume): SourceEntity? =
        sourceDao.observeAll().first().firstOrNull { candidate ->
            sourceBelongsToVolume(candidate.uri, volume.uuid)
        }

    /**
     * Имя источника: метка носителя, как её называет система.
     *
     * Пустую метку заменяет UUID, а не придуманное слово: имя источника хранится в базе, и русская
     * строка там не переводится (правило локализации), а имя носителя — данные системы.
     */
    private fun displayNameOf(volume: RemovableVolume): String =
        volume.label.ifBlank { volume.uuid }
}
