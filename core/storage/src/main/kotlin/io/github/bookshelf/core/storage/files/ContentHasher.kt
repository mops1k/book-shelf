package io.github.bookshelf.core.storage.files

import java.security.MessageDigest

/**
 * Полный хеш содержимого файла книги.
 *
 * Нужен поиску дублей: две книги с байт-в-байт одинаковым содержимым в разных папках — это одна и та
 * же книга, и лишнюю копию можно предложить удалить (docs/architecture.md §3.1: полный контентный
 * хеш считается лениво и только для поиска дубликатов).
 *
 * Отличие от [QuickHasher]: тот считается по метаданным и первым 64 КБ и обслуживает инкрементальный
 * скан, поэтому его можно звать на каждом проходе. Этот читает файл целиком, и вызывать его следует
 * только по явной команде пользователя.
 */
interface ContentHasher {

    /**
     * SHA-256 содержимого в шестнадцатеричном виде.
     *
     * `null`, если файл не открылся: нет доступа к папке, файл исчез, провайдер ответил ошибкой.
     * Такие книги не считаются дублями — сравнивать нечего, и удалять их «как дубли» нельзя.
     */
    fun contentHash(documentUri: String): String?
}

/**
 * Реализация на `MessageDigest` и [BookContentOpener].
 *
 * Файл читается потоком с буфером 64 КБ: книга на сотни мегабайт не должна попадать в память целиком.
 */
class DefaultContentHasher(
    private val contentOpener: BookContentOpener,
) : ContentHasher {

    override fun contentHash(documentUri: String): String? {
        val input = contentOpener.openStream(documentUri) ?: return null
        return runCatching {
            input.use { stream ->
                val digest = MessageDigest.getInstance(ALGORITHM)
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
            }
        }.getOrNull()
    }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val BUFFER_SIZE = 64 * 1024
    }
}
