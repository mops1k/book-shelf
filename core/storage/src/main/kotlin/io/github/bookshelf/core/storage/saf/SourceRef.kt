package io.github.bookshelf.core.storage.saf

/**
 * Источник книг, добавленный пользователем.
 *
 * [treeUri] — это `content://`-URI дерева SAF (`ACTION_OPEN_DOCUMENT_TREE`), а не путь файловой
 * системы: начиная с Android 11 выбрать корень накопителя, корень SD-карты или `Download`
 * через SAF нельзя («Архитектура» §4.1).
 *
 * @param id стабильный идентификатор источника (UUID), не зависит от пути и переживает перенос папки;
 * @param treeUri URI дерева, сохранённый в таблице `sources`;
 * @param displayName имя источника, которое видит пользователь;
 * @param kind тип источника: SAF-дерево, прямой путь (flavor `full`) или OPDS;
 * @param recursive обходить ли вложенные папки;
 * @param excludeGlobs glob-маски исключений (`*.tmp`, `.*`).
 */
data class SourceRef(
    val id: String,
    val treeUri: String,
    val displayName: String,
    val kind: SourceKind,
    val recursive: Boolean = true,
    val excludeGlobs: List<String> = emptyList(),
)

/** Тип источника — дискриминатор `kind` в таблице `sources` («Архитектура» §3.3). */
enum class SourceKind {

    /** Дерево SAF: доступ через `DocumentsContract`, работает во всех flavor'ах. */
    SAF_TREE,

    /** Обычный путь файловой системы: только flavor `full` с `MANAGE_EXTERNAL_STORAGE`. */
    DIRECT_PATH,

    /** Каталог OPDS: загруженные книги попадают в один из локальных источников. */
    OPDS,
}

/**
 * Состояние persistable-разрешения на источник.
 *
 * Разрешение теряется, если папка или документ перемещены либо удалены («Архитектура» §4.1).
 * Скан в этом случае не падает: UI показывает «Папка недоступна — выбрать заново»,
 * а книги помечаются `isMissing` до повторного подключения источника.
 */
sealed interface SourceAccess {

    /** Доступ есть: [SafTreeReader.children] вернёт содержимое дерева. */
    data object Granted : SourceAccess

    /** Доступ потерян: источник нужно переподключить. */
    data object AccessLost : SourceAccess
}
