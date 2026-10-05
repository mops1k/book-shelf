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

    /**
     * Пакет KOReader.
     *
     * Проверено на устройстве 05.10.2026: KOReader ставится как `org.koreader.launcher`. Профиль
     * найденной читалки сохраняется с `id`, равным имени пакета, поэтому без этой строки KOReader не
     * опознавался как источник прогресса (и правило «во время чтения спрашиваем только открытую
     * читалку» для него не работало).
     */
    const val PACKAGE_NAME: String = "org.koreader.launcher"
}
