package io.github.bookshelf.core.common.permission

/**
 * Дан ли приложению доступ ко всем файлам.
 *
 * От этого зависит, спросят ли человека о доступе к съёмному носителю: с доступом источник заводится
 * сам (прямой путь), без него системный выбор папки спрашивает один раз (SAF). Порог-интерфейс, а не
 * прямой вызов `Environment`: проверка принадлежит Android-слою, а решение о доступе — слою данных.
 */
fun interface AllFilesAccess {

    /** `true` — доступ выдан системой (API 30+) или разрешение на чтение памяти (API 24–29). */
    fun granted(): Boolean
}

/**
 * Доступ с учётом сборки.
 *
 * Во flavor `play` разрешения `MANAGE_EXTERNAL_STORAGE` нет (`ALLOW_ALL_FILES_ACCESS = false`,
 * ADR-006), и системный ответ тут ни при чём: сборка обязана честно сказать «доступа нет», иначе
 * приложение заведёт источник съёмного носителя «прямым путём» вместо системного выбора папки.
 *
 * @param allowedInBuild флаг сборки: разрешён ли прямой доступ к файлам вообще.
 * @param granted системная проверка; её владелец — Android-слой.
 */
fun allFilesAccessOf(allowedInBuild: Boolean, granted: () -> Boolean): AllFilesAccess =
    AllFilesAccess { allowedInBuild && granted() }
