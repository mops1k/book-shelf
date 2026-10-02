package io.github.bookshelf.core.data.removable

import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind

/**
 * Куда переносить книгу с носителя: первая обычная папка библиотеки.
 *
 * «Обычная» — значит не каталог OPDS и не папка на съёмном томе: носитель узнаётся по UUID внутри
 * адреса источника ([volumeUuidOf]). Если такой папки нет, переносить некуда, и лучше об этом
 * сказать, чем выбрать что попало.
 */
fun libraryTargetOf(sources: List<Source>): Source? = sources.firstOrNull { source ->
    source.kind != SourceKind.OPDS && volumeUuidOf(source.uri) == null
}

/**
 * Что делать с книгой при переносе.
 *
 * Решение Александра 02.10.2026: сравнивать не только имена, но и содержимое. Один и тот же файл,
 * уже лежащий в библиотеке, переносить незачем — его пропускают. Одинаковое имя при разном
 * содержимом — не повод терять данные: файл переносится под свободным именем.
 */
sealed interface TransferDecision {

    /** Дубля нет и имя свободно: переносим как есть. */
    data object Move : TransferDecision

    /** Такой файл (совпадает содержимое) в библиотеке уже есть: пропускаем. */
    data object SkipDuplicate : TransferDecision

    /** Имя занято другим файлом: переносим под именем с суффиксом. */
    data class Rename(val base: String) : TransferDecision
}

/**
 * Решение о переносе: сначала содержимое, потом имя.
 *
 * @param duplicateFound в библиотеке есть файл с тем же содержимым (сравнение по размеру и SHA-256);
 * @param nameTaken в целевой папке уже занято имя файла;
 * @param base имя файла, которое нужно освободить при [TransferDecision.Rename].
 */
fun transferDecision(
    duplicateFound: Boolean,
    nameTaken: Boolean,
    base: String,
): TransferDecision = when {
    duplicateFound -> TransferDecision.SkipDuplicate
    nameTaken -> TransferDecision.Rename(base)
    else -> TransferDecision.Move
}

/**
 * Свободное имя файла: `Солярис.fb2` → `Солярис (1).fb2`, если первое занято.
 *
 * Расширение сохраняется в конце: имя книги читает и человек, и разбор формата.
 * Если свободного имени не нашлось за [limit] попыток, возвращается последнее — вставка всё равно
 * обязана закончиться, а не уйти в бесконечный поиск.
 */
fun uniqueFileName(name: String, limit: Int = 100, isTaken: (String) -> Boolean): String {
    if (!isTaken(name)) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    var candidate = name
    for (index in 1..limit) {
        candidate = "$base ($index)$extension"
        if (!isTaken(candidate)) return candidate
    }
    return candidate
}
