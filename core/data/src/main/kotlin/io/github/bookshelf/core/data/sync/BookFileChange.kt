package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.model.ParseState

/**
 * Строка книги после того, как её файл изменился.
 *
 * Скан видит изменение по размеру и времени файла и записывает новые факты. Прежние метаданные при
 * этом больше не верны, поэтому разбор помечается незавершённым: без сброса [ParseState]
 * `MetadataWorker` выходит сразу на `parseState = OK`, и книга навсегда остаётся с названием,
 * автором и обложкой прежнего файла (дефект подтверждён чтением кода 30.09.2026).
 *
 * Обложка обнуляется вместе с файлом кэша: адрес удалённой картинки оставил бы плитку пустой.
 * Отметка оптимизации снимается по той же причине: она относится к конкретной версии файла.
 *
 * Вынесено из воркера отдельной функцией, чтобы решение проверялось тестом без обхода дерева SAF.
 */
@Suppress("LongParameterList")
fun BookEntity.afterFileChange(
    documentUri: String,
    relativePath: String,
    fileName: String,
    extension: String,
    mimeType: String?,
    sizeBytes: Long,
    lastModified: Long,
    now: Long,
): BookEntity = copy(
    documentUri = documentUri,
    // Путь с папками, а не имя файла: по `relativePath` карточка показывает «Путь», а полка
    // группирует книги «По папкам».
    relativePath = relativePath,
    fileName = fileName,
    extension = extension,
    mimeType = mimeType ?: this.mimeType,
    coverPath = null,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    updatedAt = now,
    lastSeenAt = now,
    parseState = ParseState.PENDING,
    parseError = null,
    // Файл другой — значит прежняя проверка оптимизацией к нему не относится.
    optimizedAt = null,
)
