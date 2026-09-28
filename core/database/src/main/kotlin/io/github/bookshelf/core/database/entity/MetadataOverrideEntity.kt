package io.github.bookshelf.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ручные правки метаданных книги (docs/architecture.md §3.1, §4.4).
 *
 * Живут отдельной таблицей, а не в [BookEntity], потому что пересканирование меняет файл и
 * перезаписывает встроенные метаданные — но **не должно затирать правки пользователя**. Приоритет
 * при слиянии: override → метаданные файла → имя файла (см. `MetadataOverrideRepository`).
 *
 * Одна строка на книгу, поэтому `bookId` одновременно первичный ключ и внешний ключ. Каскадное
 * удаление обязательно: переопределение книги, которой больше нет, — мусор, но хранить его годами
 * опасно — при восстановлении того же `documentUri` (например, после переустановки источника)
 * пользователь получил бы правки, относящиеся к прежнему содержимому.
 *
 * Колонки `nullable`: `null` означает «пользователь не трогал это поле», что не то же самое, что
 * «пользователь очистил поле». Различить их одной таблицей нельзя, поэтому очистка отдельного
 * поля не поддерживается сознательно — это решение пользователя приоритетнее.
 */
@Entity(
    tableName = "metadata_overrides",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bookId"])],
)
data class MetadataOverrideEntity(
    @PrimaryKey val bookId: String,
    val title: String? = null,
    val subtitle: String? = null,
    val authorsText: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val language: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val description: String? = null,
    val isbn: String? = null,
    val pageCount: Int? = null,
    /** Когда пользователь сохранил правку — для показа «изменено вручную» и сортировки. */
    val updatedAt: Long,
)
