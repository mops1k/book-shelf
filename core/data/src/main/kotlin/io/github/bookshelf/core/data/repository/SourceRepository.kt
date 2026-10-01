package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Source
import kotlinx.coroutines.flow.Flow

/**
 * Источники книг: папки SAF и прямые пути (docs/architecture.md §4.1).
 *
 * Добавление источника обязано завершиться взятием persistable-разрешения — иначе после
 * перезапуска папка «потеряется»; `addSafSource` инкапсулирует и добавление, и первичный скан
 * (скан запускается воркером, см. [ScanRepository]).
 *
 * Удаление источника **не удаляет книги**: они становятся `isMissing` до повторного подключения
 * или очистки по сроку (ADR-010).
 */
interface SourceRepository {

    fun observeSources(): Flow<List<Source>>

    /** Добавить папку SAF: `takePersistableUriPermission` + запись в `sources` + первичный скан. */
    suspend fun addSafSource(uri: String, displayName: String): AppResult<Source>

    /**
     * Добавить источник, доступ к которому уже есть: разрешение не запрашивается.
     *
     * Нужно съёмному носителю: с доступом ко всем файлам система уже пускает в дерево тома, и
     * спрашивать человека было бы лишним вопросом (решение Александра 01.10.2026 — «сначала прямой
     * путь, иначе SAF»). Без такого доступа источник заводит [addSafSource] после выбора папки.
     */
    suspend fun addGrantedSource(uri: String, displayName: String): AppResult<Source>

    /** Убрать источник из библиотеки (книги помечаются пропавшими, но не удаляются). */
    suspend fun removeSource(sourceId: String): AppResult<Unit>

    /** Запустить повторный скан источника. */
    suspend fun rescan(sourceId: String): AppResult<Unit>

    /**
     * Есть ли у приложения сохранённое право записи в папку источника.
     *
     * Без него скачивание книги в эту папку провайдер отклонит (`Permission Denial`): так бывает
     * после переноса папки или если приложение сохранило доступ только на чтение.
     */
    suspend fun hasWriteAccess(sourceId: String): Boolean
}
