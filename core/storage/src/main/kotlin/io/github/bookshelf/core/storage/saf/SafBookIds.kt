package io.github.bookshelf.core.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest

/**
 * Идентификатор книги для документа SAF: `doc:` + SHA-256 идентификатора документа.
 *
 * Одна функция на весь проект, потому что идентификатор обязан совпадать у всех, кто заводит книгу:
 * скан ([ScanSourceWorker]) и OPDS-загрузка ([OpdsDownloadWorker]). Раньше скан хэшировал
 * `documentId`, а загрузка — полный `documentUri`: для одного и того же файла получались разные id,
 * и книга могла появиться на полке дважды (или загрузка ссылалась на несуществующую строку).
 *
 * Хэш, а не сам идентификатор: `documentId` содержит слэши («primary:Books/Имя.fb2»), а id
 * подставляется в маршрут `bookdetails/{id}` — маршрут распался бы на два сегмента.
 */
fun bookIdOfDocumentId(documentId: String): String =
    BOOK_ID_PREFIX + sha256Hex(documentId)

/**
 * То же для адреса документа: идентификатор извлекается из `content://…/document/…`.
 *
 * `null` — адрес не от SAF (например, `file://`): такой документ в библиотеку не попадает, и
 * выдумывать ему идентификатор нельзя.
 */
fun bookIdOfDocumentUri(documentUri: String): String? = runCatching {
    bookIdOfDocumentId(DocumentsContract.getDocumentId(Uri.parse(documentUri)))
}.getOrNull()

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val BOOK_ID_PREFIX = "doc:"
