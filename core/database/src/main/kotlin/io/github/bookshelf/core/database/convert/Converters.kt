package io.github.bookshelf.core.database.convert

import androidx.room.TypeConverter
import io.github.bookshelf.core.database.model.BookmarkKind
import io.github.bookshelf.core.database.model.CollectionType
import io.github.bookshelf.core.database.model.MetadataSource
import io.github.bookshelf.core.database.model.OpdsDownloadState
import io.github.bookshelf.core.database.model.ParseState
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.database.model.ScanRunStatus
import io.github.bookshelf.core.database.model.SourceKind

/** Разделитель элементов списка: unit separator, в glob/MIME-строках не встречается. */
private const val LIST_SEPARATOR = "\u001F"

/** Разделитель границ диапазона. */
private const val RANGE_SEPARATOR = ".."

/**
 * Конвертеры Room для enum'ов и простых коллекций.
 *
 * Enum'ы хранятся именем константы (`TEXT`): это читаемо в дампе БД и не ломается при
 * перестановке значений. Обратное преобразование терпимо к мусору в столбце и возвращает
 * `null`, а не падает: библиотека не должна падать из-за испорченной записи.
 */
class Converters {

    @TypeConverter
    fun parseStateToString(value: ParseState?): String? = value?.name

    @TypeConverter
    fun stringToParseState(value: String?): ParseState? = enumFromName(value)

    @TypeConverter
    fun metadataSourceToString(value: MetadataSource?): String? = value?.name

    @TypeConverter
    fun stringToMetadataSource(value: String?): MetadataSource? = enumFromName(value)

    @TypeConverter
    fun readingStatusToString(value: ReadingStatus?): String? = value?.name

    @TypeConverter
    fun stringToReadingStatus(value: String?): ReadingStatus? = enumFromName(value)

    @TypeConverter
    fun sourceKindToString(value: SourceKind?): String? = value?.name

    @TypeConverter
    fun stringToSourceKind(value: String?): SourceKind? = enumFromName(value)

    @TypeConverter
    fun collectionTypeToString(value: CollectionType?): String? = value?.name

    @TypeConverter
    fun stringToCollectionType(value: String?): CollectionType? = enumFromName(value)

    @TypeConverter
    fun scanRunStatusToString(value: ScanRunStatus?): String? = value?.name

    @TypeConverter
    fun stringToScanRunStatus(value: String?): ScanRunStatus? = enumFromName(value)

    @TypeConverter
    fun opdsDownloadStateToString(value: OpdsDownloadState?): String? = value?.name

    @TypeConverter
    fun stringToOpdsDownloadState(value: String?): OpdsDownloadState? = enumFromName(value)

    @TypeConverter
    fun bookmarkKindToString(value: BookmarkKind?): String? = value?.name

    @TypeConverter
    fun stringToBookmarkKind(value: String?): BookmarkKind? = enumFromName(value)

    /** Список строк (MIME-типы, glob-шаблоны) — одной строкой через [LIST_SEPARATOR]. */
    @TypeConverter
    fun stringListToString(value: List<String>?): String? = value?.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun stringToStringList(value: String?): List<String>? = value?.split(LIST_SEPARATOR)

    /** Целочисленный диапазон (например, годы) — строкой `start..end`. */
    @TypeConverter
    fun intRangeToString(value: IntRange?): String? =
        value?.let { "${it.first}$RANGE_SEPARATOR${it.last}" }

    @TypeConverter
    fun stringToIntRange(value: String?): IntRange? =
        value?.split(RANGE_SEPARATOR)
            ?.takeIf { it.size == 2 }
            ?.let { parts ->
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                if (start != null && end != null) start..end else null
            }
}

/** Безопасный `valueOf`: неизвестное значение в БД не должно ронять чтение строки. */
private inline fun <reified T : Enum<T>> enumFromName(name: String?): T? =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
