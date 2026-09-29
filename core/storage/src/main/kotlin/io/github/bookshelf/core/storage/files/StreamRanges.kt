package io.github.bookshelf.core.storage.files

import java.io.InputStream

/**
 * Прочитать до [length] байт начиная с [offset].
 *
 * Зачем свой цикл, а не `InputStream.readNBytes`: тот появился только в API 33, а `minSdk` проекта —
 * 24. На Android 7–12 вызов упал бы `NoSuchMethodError` — и упал бы в воркере разбора метаданных,
 * то есть у человека, который просто открыл папку с книгами. Дефект нашёл lint (`NewApi`), на нашем
 * единственном устройстве (Android 14) он не проявлялся.
 *
 * Читаем циклом ещё и потому, что один вызов `read` вправе вернуть меньше запрошенного — так ведут
 * себя сетевые источники и трубы облачных провайдеров; одиночное чтение дало бы обрезанный диапазон.
 *
 * Возвращается столько байт, сколько есть: конец файла — не ошибка, а обычный край диапазона.
 * Меньше, чем просили, значит «файл кончился» — вызывающий это учитывает.
 */
fun InputStream.readRange(offset: Long, length: Int): ByteArray {
    if (length <= 0) return ByteArray(0)
    var remaining = offset
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped <= 0) return ByteArray(0)
        remaining -= skipped
    }
    val buffer = ByteArray(length)
    var filled = 0
    while (filled < length) {
        val read = read(buffer, filled, length - filled)
        if (read <= 0) break
        filled += read
    }
    return if (filled == length) buffer else buffer.copyOf(filled)
}
