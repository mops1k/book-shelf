package io.github.bookshelf.core.datastore.serialization

/**
 * Хранилище профилей читалок в виде JSON (docs/architecture.md §7, ADR-005).
 *
 * Профили (id, имя, package/activity, поддерживаемые MIME, extras) сериализуются
 * kotlinx-serialization и лежат рядом с настройками. Так встроенные presets можно обновлять
 * **без миграции БД**: встроенный профиль — это JSON в приложении, а пользовательский — JSON в
 * DataStore; `reader_profiles` в Room хранит только то, что нужно для запросов/связей.
 *
 * Строки, а не готовые типы, — сознательно: формат профилей меняется чаще, чем схема настроек, и
 * `:core:datastore` не должен зависеть от `:core:readerlink` (иначе получится цикл
 * `readerlink → datastore`).
 *
 * STUB(Sprint 5): реализация на DataStore + kotlinx-serialization, с валидацией и деградацией к
 *  встроенным presets при битом JSON.
 */
interface ReaderProfileStore {

    /** Прочитать список JSON-профилей (пустой список — профилей нет). */
    suspend fun read(): List<String>

    /** Полностью заменить список JSON-профилей. */
    suspend fun write(json: List<String>)
}
