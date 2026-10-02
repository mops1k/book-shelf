package io.github.bookshelf.core.readerlink.profile

import io.github.bookshelf.core.readerlink.mime.MimeTypes

import kotlinx.serialization.Serializable

/**
 * Профиль внешней читалки — приложения, которое открывает книгу по `Intent`.
 *
 * Профили хранятся как JSON (kotlinx-serialization), а не набором колонок: это позволяет
 * обновлять встроенные presets вместе с приложением без миграции БД и даёт пользователю
 * создавать собственные профили (docs/architecture.md §7). Таблица `reader_profiles` остаётся
 * простой: стабильный `id` + JSON-полезная нагрузка.
 *
 * @param id стабильный идентификатор профиля (`fbreader`, `koreader`, …); по нему хранится
 *   «читалка по умолчанию для формата» и выбор для конкретной книги.
 * @param name отображаемое имя.
 * @param packageName Android-пакет читалки.
 * @param activityName явная activity для `Intent.setClassName`; null — разрешаем через
 *   `PackageManager` по action/MIME.
 * @param mimeTypes MIME-типы, которые читалка декларирует; используются при фильтрации кандидатов.
 * @param supportsContentUri принимает ли читалка `content://`; иначе нужен `file://` или
 *   временная копия файла.
 * @param extrasJson дополнительные возможности (actions, порядок способов URI, сервисы L2/L3)
 *   в виде JSON; null — дополнительных данных нет.
 * @param isBuiltIn встроенный preset: пользователь может его отключить, но не удалить.
 * @param enabled включён ли профиль; выключенный не участвует в выборе.
 * @param priority приоритет при прочих равных; больше — предпочтительнее.
 */
@Serializable
data class ReaderProfile(
    val id: String,
    val name: String,
    val packageName: String,
    val activityName: String?,
    val mimeTypes: List<String>,
    val supportsContentUri: Boolean,
    val extrasJson: String?,
    val isBuiltIn: Boolean,
    val enabled: Boolean = true,
    val priority: Int = 0,
)

/**
 * Умеет ли читалка открыть формат [extension].
 *
 * Сравниваются ОБЪЯВЛЕННЫЕ типы (`MimeTypes.declaredMimeTypesFor`), а не тот, который мы
 * отправляем: для FB2 отправляется `application/octet-stream`, и по нему «умеет» любое
 * приложение — включая галерею и словарь.
 */
fun ReaderProfile.supportsExtension(extension: String): Boolean {
    val declared = MimeTypes.declaredMimeTypesFor(extension)
    return mimeTypes.any { mime -> mime.trim().lowercase() in declared }
}

/** Форматы, которые читалка умеет открывать: расширения, чьи объявленные типы она заявила. */
fun ReaderProfile.supportedExtensions(): Set<String> =
    MimeTypes.knownExtensions().filterTo(mutableSetOf()) { extension -> supportsExtension(extension) }
