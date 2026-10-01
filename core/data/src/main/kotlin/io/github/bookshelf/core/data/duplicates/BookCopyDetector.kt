package io.github.bookshelf.core.data.duplicates

import io.github.bookshelf.core.storage.files.ContentHasher
import javax.inject.Inject

/**
 * Книга библиотеки, которая может оказаться оригиналом копии.
 *
 * Отпечаток читается из строки книги: если он там есть, файл не перечитывается. `null` — отпечаток
 * ещё не считали, и его посчитает [BookCopyDetector].
 */
data class KnownBookFile(
    val id: String,
    val documentUri: String,
    val sizeBytes: Long,
    val contentHash: String? = null,
)

/**
 * Совпадение нового файла с уже известной книгой.
 *
 * @param originalBookId книга, чьё содержимое повторяет файл: ей принадлежит прогресс чтения.
 * @param contentHash отпечаток содержимого, по которому совпадение и нашлось.
 */
data class CopyMatch(
    val originalBookId: String,
    val contentHash: String,
)

/**
 * «Этот файл — копия уже известной книги?»
 *
 * Зачем: читалка (и не только она) кладёт байт-в-байт копию книги внутрь нашей библиотеки —
 * `Books/<дата>/<название>.fb2.zip` (FBReader, 30.09–01.10.2026). Такая копия попадала на полку
 * второй книгой (92 вместо 91), а прогресс чтения уходил на неё, а не на оригинал.
 *
 * Решает содержимое, а не имя и не путь: копию читалка называет по названию книги, поэтому прежнее
 * правило «совпали имя файла и размер» (`BookDao.countByFileNameAndSize`) её не поймало.
 *
 * Порядок работы выбран так, чтобы не читать файлы зря:
 * 1. пустой размер сравнивать нечем — сразу «не копия»;
 * 2. без книги того же размера содержимое совпасть не может — файлы не читаются вовсе;
 * 3. отпечаток кандидата считается один раз, отпечатки книг того же размера берутся из строк, если
 *    они уже посчитаны ([remember] вызывается для тех, что пришлось посчитать сейчас);
 * 4. недоступный файл копией не считается: сравнивать нечего, и прятать книгу из-за ошибки чтения
 *    нельзя.
 *
 * Класс не знает ни про базу, ни про Android: список книг того же размера ему передаёт вызывающий,
 * поэтому правило проверяется тестом без файловой системы.
 */
class BookCopyDetector @Inject constructor(
    private val contentHasher: ContentHasher,
) {

    /**
     * Оригинал для файла-кандидата или `null`, если копией он не является.
     *
     * @param candidateUri адрес нового файла.
     * @param candidateSize размер нового файла; `0` — сравнивать нечего.
     * @param known книги библиотеки; вызывающий передаёт их с тем же размером (дешёвый отбор в базе),
     *   но проверка размера повторяется и здесь — правило не должно зависеть от вызывающего.
     * @param remember вызывается, когда отпечаток книги пришлось посчитать сейчас: вызывающий
     *   сохраняет его в строке книги, чтобы следующий скан не перечитывал файл целиком.
     */
    suspend fun findOriginal(
        candidateUri: String,
        candidateSize: Long,
        known: List<KnownBookFile>,
        remember: suspend (bookId: String, contentHash: String) -> Unit = { _, _ -> },
    ): CopyMatch? {
        if (candidateSize <= 0L) return null
        val sameSize = known.filter { candidate -> candidate.sizeBytes == candidateSize }
        if (sameSize.isEmpty()) return null
        val candidateHash = contentHasher.contentHash(candidateUri) ?: return null
        val original = sameSize
            .firstOrNull { candidate -> hashOf(candidate, remember) == candidateHash }
            ?: return null
        return CopyMatch(originalBookId = original.id, contentHash = candidateHash)
    }

    /** Отпечаток книги: из строки, а если его там нет — считается и запоминается. */
    private suspend fun hashOf(
        known: KnownBookFile,
        remember: suspend (String, String) -> Unit,
    ): String? =
        known.contentHash
            ?: contentHasher.contentHash(known.documentUri)?.also { computed ->
                remember(known.id, computed)
            }
}
