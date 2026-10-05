package io.github.bookshelf.core.readerlink.koreader

/**
 * Где KOReader хранит состояние книги.
 *
 * Правило взято из исходников KOReader (`frontend/docsettings.lua`) и проверено на устройстве
 * (Bigme HiBreak, 05.10.2026):
 * * папка — путь книги **без последнего расширения** плюс `.sdr`;
 * * файл — `metadata.<последнее расширение>.lua`; если расширения нет, KOReader подставляет `_`.
 *
 * Поэтому для `Книга.fb2.epub` получается `Книга.fb2.sdr/metadata.epub.lua`, а для `Книга.fb2.zip` —
 * `Книга.fb2.sdr/metadata.zip.lua`: расширением считается последнее (`zip`), а в имени папки оно
 * срезано. Это неочевидно и ломается при любой «очевидной» правке — на этом и стоят тесты.
 */
object KoReaderSidecar {

    /** Суффикс папки-соседа книги. */
    const val FOLDER_SUFFIX: String = ".sdr"

    /** Имя файла, когда у книги нет расширения: так делает сам KOReader. */
    const val NO_EXTENSION: String = "_"

    /**
     * Путь sidecar-файла относительно папки книги.
     *
     * @param fileName имя файла книги (папки отбрасываются: sidecar лежит там же, где книга).
     */
    fun siblingOf(fileName: String): String {
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val extension = if (dot >= 0) name.substring(dot + 1) else NO_EXTENSION
        return "$base$FOLDER_SUFFIX/metadata.$extension.lua"
    }
}
