package io.github.bookshelf.core.readerlink.koreader

/**
 * Идентификатор профиля KOReader.
 *
 * Нужен вне каталога пресетов: по профилю читалки сессия чтения понимает, какой источник прогресса
 * разрешено спрашивать, пока чтение идёт (требование Александра, 05.10.2026). Строка совпадает с
 * `id` профиля в [io.github.bookshelf.core.readerlink.profile.ReaderProfilePresets] — это одно и то
 * же значение, и разойтись они не должны.
 */
object KoReaderPreset {

    const val ID: String = "koreader"
}
