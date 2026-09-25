package io.github.bookshelf.core.covers.cache

import io.github.bookshelf.core.covers.CoverCache.CoverKind
import java.io.File

/**
 * Имена и расположение файлов кэша обложек (docs/architecture.md §5).
 *
 * Чистые функции без ввода-вывода: их использует и дисковый кэш, и Coil (через
 * [io.github.bookshelf.core.covers.CoverCache.fileFor]), и тесты.
 *
 * Безопасность: `bookId` приходит из БД (UUID), но кэш не должен доверять данным слепо —
 * скомпрометированная или импортированная библиотека не имеет права записать файл за пределами
 * `filesDir/covers`. Поэтому идентификатор проверяется на «путь наружу» (`../`, разделители,
 * ведущая точка, абсолютные пути, управляющие символы).
 */
object CoverPaths {

    /** Каталог кэша внутри `filesDir` (сам каталог создаёт `:app`, см. `AppModule`). */
    const val DIRECTORY_NAME = "covers"

    /** Единственный формат кэша: WebP даёт лучший размер при lossy q80 (docs/architecture.md §4.4). */
    const val EXTENSION = "webp"

    /** Суффикс тайла сетки. */
    const val TILE_SUFFIX = "_t"

    /** Суффикс временного файла для атомарной записи (`.tmp` + `rename`). */
    const val TEMP_SUFFIX = ".tmp"

    /** Разумный предел длины идентификатора: UUID — 36 символов, запас на будущие схемы. */
    private const val MAX_BOOK_ID_LENGTH = 128

    /** Разрешены только буквы, цифры, `_`, `-`, `.`; первый символ — буква или цифра. */
    private val SAFE_BOOK_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    /** Каталог кэша внутри переданного `filesDir`. */
    fun coversDir(filesDir: File): File = File(filesDir, DIRECTORY_NAME)

    /**
     * Имя файла обложки: `{bookId}.webp` для [CoverKind.FULL] и `{bookId}_t.webp` для [CoverKind.TILE].
     *
     * @throws IllegalArgumentException если [bookId] небезопасен ([isSafeBookId] == false).
     */
    fun fileName(bookId: String, kind: CoverKind): String {
        require(isSafeBookId(bookId)) { "Небезопасный bookId для кэша обложек: '$bookId'" }
        return when (kind) {
            CoverKind.FULL -> "$bookId.$EXTENSION"
            CoverKind.TILE -> "$bookId$TILE_SUFFIX.$EXTENSION"
        }
    }

    /** Полный путь файла обложки внутри [cacheDir]. */
    fun fileFor(cacheDir: File, bookId: String, kind: CoverKind): File =
        File(cacheDir, fileName(bookId, kind))

    /** Временный файл рядом с целевым: `{target}.tmp` — запись + `rename` в пределах одной ФС. */
    fun tempFileFor(target: File): File = File(target.path + TEMP_SUFFIX)

    /**
     * Безопасен ли идентификатор книги как имя файла.
     *
     * Отдельно запрещена подстрока `..`: без разделителей она безобидна, но защита от обхода
     * каталога должна быть очевидной и не зависеть от тонкостей разбора путей.
     */
    fun isSafeBookId(bookId: String): Boolean =
        bookId.length in 1..MAX_BOOK_ID_LENGTH &&
            SAFE_BOOK_ID.matches(bookId) &&
            !bookId.contains("..")
}
