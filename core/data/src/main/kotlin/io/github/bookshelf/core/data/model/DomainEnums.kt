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
 * Подпись статуса чтения для экранов: код `UNREAD` читался бы как код, а не как состояние.
 *
 * Одна реализация на полку, карточку книги и бейдж умной полки. До неё эта подпись лежала
 * тремя независимыми копиями (полка, карточка, `SmartRules`) и уже начала разъезжаться.
 */
val ReadingStatus.label: String
    get() = when (this) {
        ReadingStatus.UNREAD -> "не прочитано"
        ReadingStatus.READING -> "читаю"
        ReadingStatus.READ -> "прочитано"
    }

/**
 * Подпись статуса для бейджа на обложке: «не прочитано», «читаю 42%», «прочитано».
 *
 * @param progressPercent прогресс чтения 0..100; `null` — читалка его не отдаёт, и процент
 *   не выдумывается: «читаю 0%» на свежей книге читалось бы как ошибка.
 */
fun ReadingStatus.badge(progressPercent: Int? = null): String = when {
    this != ReadingStatus.READING -> label
    progressPercent == null -> label
    else -> "$label $progressPercent%"
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
