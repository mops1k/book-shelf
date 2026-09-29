package io.github.bookshelf.core.data.export

import android.net.Uri
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection

/**
 * Выгрузка библиотеки в JSON-файл, выбранный человеком.
 *
 * Разделено на два шага, потому что шаги проверяются по-разному:
 * * [snapshot] — что именно лежит в библиотеке: книги с авторами, жанрами и прогрессом плюс полки.
 *   Проверяется на настоящей базе (in-memory Room), без Android-хранилища;
 * * [exportTo] — запись готового файла в документ, который вернул системный диалог «создать
 *   документ». Проверяется подменённым потоком вывода.
 *
 * Собирать файл и писать его одним неделимым действием нельзя: тогда «что попало в файл» нельзя
 * проверить, не разбирая записанный JSON, и любая ошибка записи выглядела бы как ошибка данных.
 */
interface LibraryExportRepository {

    /** Снимок библиотеки для файла: книги и полки в состоянии на момент вызова. */
    suspend fun snapshot(): LibraryExportContent

    /**
     * Записать файл экспорта в [destination] (документ из диалога SAF).
     *
     * @return сколько книг и полок попало в файл — это число показывается человеку, чтобы он
     *   видел, что выгрузка не пустая и не обрезанная.
     */
    suspend fun exportTo(destination: Uri): AppResult<LibraryExportSummary>
}

/**
 * Что попадает в файл.
 *
 * [genres] — отдельная карта «книга → жанры»: в доменной модели книги жанров нет, они лежат
 * в связях `book_tags`/`tags`. [Book.progressPercent] уже заполнен из `reading_progress`.
 */
data class LibraryExportContent(
    val books: List<Book>,
    val collections: List<Collection>,
    val genres: Map<String, List<String>> = emptyMap(),
)

/** Итог выгрузки: сколько книг и полок записано в файл. */
data class LibraryExportSummary(
    val books: Int,
    val collections: Int,
)
