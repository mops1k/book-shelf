package io.github.bookshelf.core.readerlink.fbreader

/**
 * Наш интерфейс к API-сервису FBReader (уровень L2).
 *
 * Это собственный Kotlin-интерфейс, а не сгенерированные AIDL-типы: адаптер к
 * `org.geometerplus.android.fbreader.api.ApiInterface` появится в Sprint 7, когда в
 * `src/main/aidl/` будут вендорены public-domain AIDL-файлы FBReader (см. README рядом с ними).
 * Благодаря прослойке доменный слой не зависит ни от AIDL, ни от `Parcelable`.
 *
 * Важное ограничение оригинала: `getBookProgress()` относится к **текущей открытой** книге;
 * перегрузки с идентификатором нет. Поэтому прогресс читается при событии API или при возврате
 * пользователя в наше приложение и сохраняется для книги, определённой по `getBookFilePath()`
 * или `getBookHash()` (docs/reader-integration.md §3.1).
 */
interface FbReaderApi {

    /** Прогресс текущей книги, 0..1. */
    suspend fun getBookProgress(): Float

    /** Уникальный идентификатор текущей книги; null, если книга не открыта. */
    suspend fun getBookUniqueId(): String?

    /** Хеш текущей книги — ключ сопоставления, когда идентификатор не подходит. */
    suspend fun getBookHash(): String?

    /** Путь текущей книги — основной ключ сопоставления с нашей записью. */
    suspend fun getBookFilePath(): String?

    /** Открыть текущую книгу на заданной позиции. */
    suspend fun setPageStart(position: TextPosition)
}

/**
 * Наш интерфейс к сервису библиотеки FBReader (уровень L3).
 *
 * Даёт прогресс по каждой книге (`getStoredPosition`), обложку и описание как фолбэк, а также
 * данные для сверки метаданных. Это самый ценный уровень интеграции: без него полка знает
 * прогресс только вручную (docs/reader-integration.md §4).
 *
 * AIDL-комментарий FBReader объявляет `LibraryInterface` стабильным интерфейсом для плагинов:
 * существующие сигнатуры не меняются, новые методы добавляются только в конец.
 */
interface FbReaderLibraryApi {

    /** Найти книгу FBReader по хешу. */
    suspend fun getBookByHash(hash: String): FbReaderBook?

    /** Найти книгу FBReader по реальному пути файла. */
    suspend fun getBookByFile(filePath: String): FbReaderBook?

    /** Посчитать хеш FBReader для файла; [force] — пересчитать, даже если он уже есть. */
    suspend fun getHash(filePath: String, force: Boolean = false): String?

    /** Сохранённая позиция чтения по конкретной книге — то, ради чего нужен L3. */
    suspend fun getStoredPosition(book: FbReaderBook): FbReaderPosition?

    /** Записать позицию (двусторонняя синхронизация; включена только по явной настройке). */
    suspend fun storePosition(book: FbReaderBook, position: FbReaderPosition)

    /** Обложка из библиотеки FBReader — фолбэк, если наш парсер её не извлёк. */
    suspend fun getCover(book: FbReaderBook, maxWidth: Int, maxHeight: Int): ByteArray?

    /** Аннотация из FBReader, если в файле её нет. */
    suspend fun getDescription(book: FbReaderBook): String?

    /** Авторы, известные библиотеке FBReader, — для сверки метаданных. */
    suspend fun authors(): List<String>

    /** Серии, известные библиотеке FBReader, — для сверки метаданных. */
    suspend fun series(): List<String>
}

/**
 * Книга в терминах FBReader.
 *
 * @param id внутренний идентификатор библиотеки FBReader.
 * @param title название; null, если библиотека его не знает.
 * @param filePath реальный путь файла; null для книг без доступного файла.
 * @param hash хеш FBReader; null, если ещё не посчитан.
 */
data class FbReaderBook(
    val id: Long,
    val title: String?,
    val filePath: String?,
    val hash: String?,
)

/**
 * Позиция в тексте, повторяющая `TextPosition` из AIDL FBReader
 * (`paragraphIndex`, `elementIndex`, `charIndex`).
 */
data class TextPosition(
    val paragraphIndex: Int,
    val elementIndex: Int,
    val charIndex: Int,
)

/**
 * Позиция с отметкой времени, повторяющая `PositionWithTimestamp` из AIDL FBReader.
 *
 * @param position собственно позиция в тексте.
 * @param timestampMillis время сохранения позиции; null, если библиотека его не отдала.
 */
data class FbReaderPosition(
    val position: TextPosition,
    val timestampMillis: Long?,
)
