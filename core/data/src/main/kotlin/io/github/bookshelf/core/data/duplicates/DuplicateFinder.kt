package io.github.bookshelf.core.data.duplicates

import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.storage.files.ContentHasher
import javax.inject.Inject

/**
 * Группа книг с одинаковым содержимым файлов.
 *
 * @param keep копия, которую предлагается оставить.
 * @param remove копии, которые предлагается удалить с устройства.
 */
data class DuplicateGroup(
    val keep: Book,
    val remove: List<Book>,
)

/**
 * Итог проверки библиотеки на дубли.
 *
 * @param groups группы книг с байт-в-байт одинаковым содержимым.
 * @param broken книги с файлами нулевого размера: их нельзя ни открыть, ни сравнить, поэтому они
 *   показываются отдельно и удаляются только по отдельному решению пользователя.
 */
data class DuplicateReport(
    val groups: List<DuplicateGroup> = emptyList(),
    val broken: List<Book> = emptyList(),
) {

    /** Сколько копий предлагается удалить как дубли. */
    val duplicatesToRemove: Int get() = groups.sumOf { group -> group.remove.size }

    /** Книг, файлы которых не читаются (нулевой размер). */
    val brokenCount: Int get() = broken.size

    /** Ничего не найдено: и дублей, и битых файлов нет. */
    val isEmpty: Boolean get() = groups.isEmpty() && broken.isEmpty()
}

/**
 * Поиск книг с одинаковым содержимым.
 *
 * Порядок работы выбран так, чтобы не читать файлы зря:
 * 1. книги с нулевым размером сразу уходят в [DuplicateReport.broken] — сравнивать в них нечего;
 * 2. остальные группируются по размеру: если размер у книги уникален, её содержимое не может
 *    совпасть ни с чьим, и хеш для неё не считается вовсе;
 * 3. внутри групп одинакового размера считается полный хеш содержимого ([ContentHasher]).
 *
 * Класс не знает про базу: список книг ему передают, поэтому правило «что оставить» проверяется
 * тестом без Android и без файловой системы.
 */
class DuplicateFinder @Inject constructor(
    private val contentHasher: ContentHasher,
) {

    /** Найти группы дублей среди [books]. */
    fun find(books: List<Book>): DuplicateReport {
        val broken = books.filter { book -> book.sizeBytes <= 0L }
        val bySize = books
            .filter { book -> book.sizeBytes > 0L }
            .groupBy { book -> book.sizeBytes }

        val groups = mutableListOf<DuplicateGroup>()
        bySize.values
            .filter { sameSize -> sameSize.size > 1 }
            .forEach { sameSize ->
                sameSize
                    .mapNotNull { book -> contentHasher.contentHash(book.documentUri)?.let { hash -> hash to book } }
                    .groupBy(keySelector = { (hash, _) -> hash }, valueTransform = { (_, book) -> book })
                    .values
                    .filter { sameContent -> sameContent.size > 1 }
                    .forEach { sameContent ->
                        val ordered = sameContent.sortedWith(KEEP_FIRST)
                        groups += DuplicateGroup(keep = ordered.first(), remove = ordered.drop(1))
                    }
            }
        return DuplicateReport(groups = groups.sortedBy { group -> group.keep.title }, broken = broken)
    }

    private companion object {

        /**
         * Что оставить: копию с более глубоким путём, то есть разложенную по папкам
         * (`Автор/Серия/Книга.fb2`), а не безымянную копию в корне или в папке даты
         * (решение Александра, 29.09.2026). При равной глубине остаётся более новая по дате
         * добавления, затем — с меньшим идентификатором, чтобы результат был предсказуемым.
         */
        val KEEP_FIRST: Comparator<Book> = compareByDescending<Book> { book ->
            book.relativePath.count { char -> char == '/' }
        }
            .thenByDescending { book -> book.addedAt }
            .thenBy { book -> book.id }
    }
}
