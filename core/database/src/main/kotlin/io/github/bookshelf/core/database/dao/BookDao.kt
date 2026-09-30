package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.relation.BookWithRelations
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к центральной таблице `books`.
 *
 * Страница полки и её счётчик принимают готовый `SELECT` (@RawQuery): условия фильтров собирает
 * `:core:data`, потому что только он знает доменные модели. Всё остальное — обычные запросы.
 */
@Dao
interface BookDao {

    /** Добавить книгу, найденную сканированием. */
    @Insert
    suspend fun insert(book: BookEntity)

    /** Пакетная вставка/обновление: скан обрабатывает книги пачками. */
    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    /** Обновить книгу целиком (после разбора метаданных или ручного редактирования). */
    @Update
    suspend fun update(book: BookEntity)

    /**
     * Книга по адресу документа.
     *
     * Нужно синхронизации прогресса: читалка отдаёт адрес открытой книги, и для наших книг это ровно
     * тот `content://`, которым мы её открыли (`books.documentUri`).
     */
    /**
     * Сколько книг уже имеют такое имя файла и размер.
     *
     * Нужно скану, чтобы не заводить книгу на копию: FBReader, открывая книгу из нашего приложения,
     * копирует файл в `Books/<дата>/` внутри нашей библиотеки (его «Куда сохранять загруженные
     * книги» указывает на ту же папку, что мы сканируем). Копия — тот же файл, у неё совпадают имя и
     * размер, и в полке она оказывалась второй книгой (30.09.2026).
     *
     * `excludeId` — книга, которую мы сейчас пишем: сама себе она копией не является.
     */
    @Query(
        "SELECT COUNT(*) FROM books WHERE fileName = :fileName AND sizeBytes = :sizeBytes " +
            "AND id != :excludeId",
    )
    suspend fun countByFileNameAndSize(fileName: String, sizeBytes: Long, excludeId: String): Int

    /**
     * Все книги одним списком.
     *
     * Нужен поиску дублей: он сравнивает содержимое файлов и не может опираться на страницу полки
     * или на поток с фильтрами.
     */
    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    /** Удалить запись; вызывается только явно, а не при исчезновении файла (`isMissing`). */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Удалить все записи библиотеки — команда «Пересканировать».
     *
     * Удаляются только строки таблицы: файлы на устройстве остаются нетронутыми, и следующий
     * скан заново их найдёт. Именно поэтому команда безопасна — в отличие от удаления файлов.
     */
    @Query("DELETE FROM books")
    suspend fun deleteAll()

    /** Наблюдать книгу по id — карточка книги. */
    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    /** Прочитать книгу по id. */
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    /**
     * Найти книгу по `documentUri` — основной шаг инкрементального сканирования
     * (docs/architecture.md §4.3). `documentUri` уникален, поэтому результат один.
     */
    @Query("SELECT * FROM books WHERE documentUri = :documentUri LIMIT 1")
    suspend fun findByDocumentUri(documentUri: String): BookEntity?

    /**
     * Отметить книги как «виденные» при сканировании.
     *
     * ВНИМАНИЕ: пустой список передавать нельзя — Room подставит `IN ()`, а SQLite не принимает
     * пустой `IN` (синтаксическая ошибка). Вызывающий обязан проверить `ids.isNotEmpty()`.
     */
    @Query("UPDATE books SET lastSeenAt = :seenAt WHERE id IN (:ids)")
    suspend fun touchLastSeen(ids: List<String>, seenAt: Long)

    /**
     * Пометить книги исчезнувшими, не удаляя запись: история чтения и коллекции сохраняются.
     *
     * ВНИМАНИЕ: пустой список передавать нельзя — `IN ()` недопустим в SQLite, см. [touchLastSeen].
     */
    @Query("UPDATE books SET isMissing = 1, missingSince = :since WHERE id IN (:ids)")
    suspend fun markMissing(ids: List<String>, since: Long)

    /**
     * Идентификаторы книг источника — для пометки пропавших после скана.
     *
     * Скан сравнивает факт посещения с содержимым таблицы, а не наоборот: иначе пришлось бы
     * держать в памяти все `documentUri` дерева, что на 2000 файлов уже заметная память.
     */
    @Query("SELECT id FROM books WHERE sourceId = :sourceId")
    suspend fun idsBySource(sourceId: String): List<String>

    /** Число книг источника — для строки источника в настройках. */
    @Query("SELECT COUNT(*) FROM books WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: String): Int

    /**
     * Страница книг для полки по произвольному `SELECT`.
     *
     * Почему `@RawQuery`, а не десяток именованных запросов: у полки восемь независимых фильтров,
     * каждый либо задан, либо нет, плюс восемь вариантов сортировки. Наборы значений ещё и
     * переменной длины, а пустой `IN ()` SQLite не принимает. Умножать комбинации в SQL нельзя,
     * поэтому SQL собирает вызывающий слой (`:core:data`), а DAO только исполняет его.
     *
     * `observedEntities` обязателен: без него Room не знает, какие таблицы читает запрос, и Flow
     * не перевыдаст страницу после изменения книг.
     */
    @RawQuery(observedEntities = [BookEntity::class])
    fun observePage(query: SupportSQLiteQuery): Flow<List<BookEntity>>

    /**
     * Общее число книг по тем же условиям, что и [observePage].
     *
     * Считается отдельным запросом, а не догоняется по размеру выборки: страница по определению
     * короче библиотеки. Условия обязаны совпадать с условиями выборки — иначе подпись
     * «Стр. 1/12» покажет число, которого на самом деле нет.
     */
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeCount(query: SupportSQLiteQuery): Flow<Int>

    /** Одноразовое чтение числа книг — для мест без наблюдения. */
    @RawQuery(observedEntities = [BookEntity::class])
    suspend fun count(query: SupportSQLiteQuery): Int

    /** Переключить «избранное» без чтения и перезаписи всей строки. */
    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Оценить книгу; `null` снимает оценку. Границы 0..5 проверяет вызывающий слой. */
    @Query("UPDATE books SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: String, rating: Int?)

    /** Отметить статус чтения; передаётся имя константы enum'а, а не сам enum. */
    @Query("UPDATE books SET readingStatus = :status WHERE id = :id")
    suspend fun setReadingStatus(id: String, status: String)


    /** Быстрый хеш для сравнения «файл изменился»; `null` — хеш ещё не посчитан. */
    @Query("SELECT quickHash FROM books WHERE id = :id")
    suspend fun getQuickHash(id: String): String?

    /** Книга вместе с авторами, коллекциями и тегами — экран карточки книги. */
    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getWithRelations(id: String): BookWithRelations?

    /** То же наблюдением: карточка должна обновляться сама, без перезапроса из UI. */
    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    fun observeWithRelations(id: String): Flow<BookWithRelations?>

    /**
     * Книги, у которых ещё не извлечены жанры (`tags`/`book_tags` пусты).
     *
     * Нужно для разового бэкфилла: у библиотек, разобранных до появления поддержки жанров,
     * `parseState = OK`, поэтому обычный разбор их пропускает, и жанры у них не появились бы.
     * Книги без жанров вовсе (например, PDF без метаданных) остаются в этом списке — поэтому
     * бэкфилл запускается один раз по флагу в настройках, а не на каждом старте.
     */
    @Query(
        """
        SELECT books.id FROM books
        LEFT JOIN book_tags ON book_tags.bookId = books.id
        WHERE book_tags.bookId IS NULL
        """,
    )
    suspend fun idsWithoutTags(): List<String>

}
