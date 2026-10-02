package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.data.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Фейк источников книг для тестов.
 *
 * Заведён по правилу ADR-012 (свои типы не мокаются): `mockk<SourceRepository>` в тестах скрывал бы
 * изменения интерфейса и привязывал проверки к внутренностям. Живёт в `:core:testing`, а не в
 * `src/testFixtures` модуля: `testFixtures` на AGP 8.7.3 не компилируют Kotlin, поэтому общие фейки
 * проекта лежат здесь.
 *
 * @param sources что отдаёт [observeSources]; список можно менять между шагами теста.
 */
class FakeSourceRepository(
    var sources: List<Source> = emptyList(),
) : SourceRepository {

    /** Источники, для которых тест запросил повторный скан. */
    val rescanned: MutableList<String> = mutableListOf()

    /** Источники, которые тест удалил. */
    val removed: MutableList<String> = mutableListOf()

    /** Источники, добавленные тестом: uri → подпись. */
    val added: MutableMap<String, String> = mutableMapOf()

    /** Ответ на [hasWriteAccess]; по умолчанию право записи есть. */
    var writeAccess: Boolean = true

    override fun observeSources(): Flow<List<Source>> = flowOf(sources)

    override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> {
        added[uri] = displayName
        val source = Source(
            id = "source-${added.size}",
            uri = uri,
            displayName = displayName,
            kind = io.github.bookshelf.core.data.model.SourceKind.SAF_TREE,
        )
        sources = sources + source
        return AppResult.success(source)
    }

    /** Адреса источников, зарегистрированных с уже имеющимся доступом (полка носителя). */
    val grantedSources: MutableList<String> = mutableListOf()

    /** Источники по обычному пути файловой системы: так заводится съёмный носитель. */
    val directSources: MutableList<String> = mutableListOf()

    override suspend fun addGrantedSource(uri: String, displayName: String): AppResult<Source> {
        grantedSources += uri
        return AppResult.success(
            Source(
                id = "granted-${grantedSources.size}",
                uri = uri,
                displayName = displayName,
                kind = SourceKind.SAF_TREE,
            ),
        )
    }

    override suspend fun addDirectPathSource(path: String, displayName: String): AppResult<Source> {
        directSources += path
        return AppResult.success(
            Source(
                id = "direct-${directSources.size}",
                uri = path,
                displayName = displayName,
                kind = SourceKind.DIRECT_PATH,
            ),
        )
    }

    override suspend fun removeSource(sourceId: String): AppResult<Unit> {
        removed += sourceId
        sources = sources.filterNot { source -> source.id == sourceId }
        return AppResult.success(Unit)
    }

    override suspend fun rescan(sourceId: String): AppResult<Unit> {
        rescanned += sourceId
        return AppResult.success(Unit)
    }

    override suspend fun hasWriteAccess(sourceId: String): Boolean = writeAccess
}
