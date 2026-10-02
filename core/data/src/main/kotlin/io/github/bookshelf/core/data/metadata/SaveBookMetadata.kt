package io.github.bookshelf.core.data.metadata

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.data.author.parseAuthors
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.metadata.writer.MetadataEdit
import javax.inject.Inject

/**
 * Причина, по которой правка не попала в файл книги.
 *
 * Код, а не готовая строка (docs/tdd.md §8): текст рождается в слое данных, переводить его здесь
 * нечем, а показывать человеку русскую строку в английском интерфейсе нельзя. Раньше тут была
 * строка вида «Нет права записи в папку» — заодно с ней терялась и специфика отказа.
 */
sealed interface MetadataWriteFailure {

    /** Книги больше нет в библиотеке: её удалили, пока экран правки был открыт. */
    data object BookNotFound : MetadataWriteFailure

    /** Провайдер отказал в записи; слова для человека выбираются по типу ошибки. */
    data class WriteFailed(val error: AppError) : MetadataWriteFailure
}

/**
 * Что получилось из правки метаданных.
 *
 * @param fileWritten правка записана в сам файл книги — значит, переживёт перенос на другое
 *   устройство (требование Александра, 29.09.2026).
 * @param fileFailure причина, по которой файл не тронут. Правка при этом в приложении сохранена,
 *   и человек обязан увидеть, что именно не удалось: «сохранено» без этой оговорки было бы враньём.
 */
data class MetadataSaveResult(
    val fileWritten: Boolean,
    val fileFailure: MetadataWriteFailure? = null,
)

/**
 * Сохранить правку метаданных: в приложении и — по просьбе — в самом файле книги.
 *
 * Порядок именно такой: сначала `books` и `metadata_overrides` (правка уже работает в приложении,
 * даже если файл записать не удалось), затем файл. Обратный порядок означал бы, что отказ провайдера
 * на запись теряет правку целиком.
 */
class SaveBookMetadata @Inject constructor(
    private val repository: BookRepository,
    private val editor: BookMetadataEditor,
) {

    suspend fun save(bookId: String, patch: MetadataPatch, writeToFile: Boolean): AppResult<MetadataSaveResult> {
        repository.updateMetadata(bookId, patch).errorOrNull()?.let { error ->
            return AppResult.failure(error)
        }
        if (!writeToFile) return AppResult.success(MetadataSaveResult(fileWritten = false))
        val book = when (val found = repository.getById(bookId)) {
            is AppResult.Success -> found.value
            is AppResult.Failure -> return found
        } ?: return AppResult.success(
            MetadataSaveResult(fileWritten = false, fileFailure = MetadataWriteFailure.BookNotFound),
        )
        return when (val written = editor.writeToFile(book, patch.toEdit())) {
            is AppResult.Success -> AppResult.success(MetadataSaveResult(fileWritten = true))
            is AppResult.Failure -> AppResult.success(
                MetadataSaveResult(
                    fileWritten = false,
                    fileFailure = MetadataWriteFailure.WriteFailed(written.error),
                ),
            )
        }
    }
}

/**
 * Патч → правка файла.
 *
 * Авторы разбираются тем же разбором, что и везде (`parseAuthors`): «Иванов, Петров» — это два
 * автора, а не один с запятой в имени.
 */
private fun MetadataPatch.toEdit() = MetadataEdit(
    title = title,
    authors = authorsText?.let(::parseAuthors),
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    description = description,
    clearSeriesIndex = clearSeriesIndex,
)
