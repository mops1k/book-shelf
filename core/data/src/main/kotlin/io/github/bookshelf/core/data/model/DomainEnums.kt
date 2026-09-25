package io.github.bookshelf.core.data.model

/**
 * Доменные перечисления, не зависящие от `:core:database` (docs/architecture.md §2, §3.1).
 *
 * В Room те же состояния хранятся entity-перечислениями с `@TypeConverter`. Дублирование
 * осознанное: feature-модули видят только `:core:data`, а публичные API модуля — только
 * `api`/интерфейсы; тащить Room-типы в UI значило бы «протечь» слою хранения наружу.
 *
 * **Маппинг entity-enum ↔ доменный enum делает `mapper/BookMappers.kt`** по стабильному `name`
 * (не по `ordinal`: порядок констант в БД может измениться при добавлении значения).
 */

/** Статус чтения книги. */
enum class ReadingStatus {
    UNREAD,
    READING,
    READ,
}

/**
 * Откуда взяты метаданные книги.
 *
 * [MANUAL] — пользователь отредактировал вручную; такие данные не перезаписываются при
 * пересканировании (docs/architecture.md §3.1).
 */
enum class MetadataOrigin {
    EMBEDDED,
    FILENAME,
    MANUAL,
    NONE,
}

/**
 * Состояние разбора файла.
 *
 * Битый файл никогда не выбрасывает исключение наружу: он остаётся в библиотеке с
 * [FAILED]/[PARTIAL] и заголовком из имени файла (docs/architecture.md §1, §4.4).
 */
enum class ParseState {
    PENDING,
    OK,
    PARTIAL,
    FAILED,
    UNSUPPORTED,
}
