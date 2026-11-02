package io.github.bookshelf.core.update.source

import io.github.bookshelf.core.update.model.ReleaseInfo

/**
 * Источник информации о последнем релизе (ADR-018).
 *
 * Порт, а не готовый клиент: в тестах проверка обновлений подменяется своим фейком, без сети
 * и без моков чужих типов. `null` означает «новее нет или ответ не понят» — оба случая для
 * пользователя одинаковы.
 */
interface ReleaseSource {

    /** Последний релиз или `null`, если его не удалось прочитать. */
    suspend fun latest(): ReleaseInfo?
}
