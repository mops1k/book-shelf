package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.removable.RemovableShelf
import io.github.bookshelf.core.data.removable.RemovableShelfRepository
import io.github.bookshelf.core.storage.volume.RemovableVolume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Фейк полки съёмного носителя для тестов.
 *
 * По правилу ADR-012 (свои типы не мокаются): вместо `mockk` — живой поток полки (носитель могут
 * вставить или вынуть прямо во время теста) и память о том, что у фейка просили. Отдельные ответы
 * на [ensureSource] и [grantTreeAccess] нужны потому, что это две разные ветки доступа: прямой путь
 * и выбор папки человеком.
 */
class FakeRemovableShelfRepository(
    shelves: List<RemovableShelf> = emptyList(),
) : RemovableShelfRepository {

    /** Живой поток полок: тест вправе вставить или вынуть носитель после создания фейка. */
    val shelvesFlow: MutableStateFlow<List<RemovableShelf>> = MutableStateFlow(shelves)

    /** Одна полка — удобная запись для тестов, где носитель один. */
    var shelf: RemovableShelf?
        get() = shelvesFlow.value.firstOrNull()
        set(value) {
            shelvesFlow.value = listOfNotNull(value)
        }

    /** Что вернуть на [ensureSource]; по умолчанию доступ ко всем файлам уже есть. */
    var ensureResult: AppResult<Unit> = AppResult.success(Unit)

    /** Что вернуть на [grantTreeAccess]; по умолчанию выбранная папка принята. */
    var treeResult: AppResult<Unit> = AppResult.success(Unit)

    /** Носители, для которых экран просил доступ: UUID. */
    val requested: MutableList<String> = mutableListOf()

    /** Выбранные человеком папки: UUID носителя → адрес дерева. */
    val treeGrants: MutableList<Pair<String, String>> = mutableListOf()

    /** Носители, об изъятии которых сообщили. */
    val ejected: MutableList<RemovableVolume> = mutableListOf()

    override fun observeShelves(): Flow<List<RemovableShelf>> = shelvesFlow

    override suspend fun ensureSource(volume: RemovableVolume): AppResult<Unit> {
        requested += volume.uuid
        return ensureResult
    }

    override suspend fun grantTreeAccess(volume: RemovableVolume, treeUri: String): AppResult<Unit> {
        treeGrants += volume.uuid to treeUri
        return treeResult
    }

    override suspend fun onEjected(volume: RemovableVolume): AppResult<Unit> {
        ejected += volume
        return AppResult.success(Unit)
    }
}
