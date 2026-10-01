package io.github.bookshelf.core.data.removable

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.storage.volume.RemovableVolume
import kotlinx.coroutines.flow.Flow

/**
 * Полка съёмного носителя: карта памяти или флешка (требование Александра 01.10.2026).
 *
 * Поведение: носитель с книгами подключили — в полках появилась полка носителя, книги видны и в
 * общей полке; носитель вынули — полка исчезла и книг с него не видно; вставили снова — всё вернулось.
 * Книги при изъятии **не удаляются**: история чтения дороже дискового места (ADR-010).
 */
interface RemovableShelfRepository {

    /** Полка носителя или `null`, если носителя нет либо полка выключена настройкой. */
    fun observeShelf(): Flow<RemovableShelf?>

    /**
     * Завести источник носителя, если доступа достаточно.
     *
     * Успех — источник создан (или уже был). Отказ [io.github.bookshelf.core.common.error.AppError.PermissionDenied]
     * означает, что доступ ко всем файлам не выдан: экран обязан предложить его дать выбором папки.
     */
    suspend fun ensureSource(volume: RemovableVolume): AppResult<Unit>

    /**
     * Человек выбрал папку носителя в системном диалоге: сохранить право и завести источник.
     *
     * Папка может быть не корнем носителя, а его подпапкой с книгами — она принимается, если
     * адрес дерева принадлежит тому же носителю ([sourceBelongsToVolume]). Папка другого места
     * отклоняется: иначе источник завёлся бы, но полка носителя его не узнала бы и продолжала бы
     * просить доступ, который человек уже дал.
     */
    suspend fun grantTreeAccess(volume: RemovableVolume, treeUri: String): AppResult<Unit>

    /** Носитель изъяли: книги его источника помечаются пропавшими, но остаются в библиотеке. */
    suspend fun onEjected(volume: RemovableVolume): AppResult<Unit>
}
