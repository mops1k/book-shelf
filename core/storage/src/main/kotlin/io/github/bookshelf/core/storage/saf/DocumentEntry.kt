package io.github.bookshelf.core.storage.saf

/**
 * Запись дерева SAF — файл или папка.
 *
 * Поля соответствуют колонкам `DocumentsContract.Document.COLUMN_*`. [sizeBytes] и
 * [lastModified] провайдер вправе не отдавать (тогда `null`), а [mimeType] у папок равен
 * `vnd.android.document/directory`. Значения приходят из одного `Cursor` на папку —
 * см. [SafTreeReader.children].
 */
data class DocumentEntry(
    val documentId: String,
    val displayName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val mimeType: String?,
) {

    /**
     * Расширение файла в нижнем регистре, без точки; `""` — расширения нет.
     *
     * Скрытые файлы (`.nomedia`) расширением не считаются: точка в начале имени — не разделитель.
     * Для составных имён (`book.fb2.zip`) возвращается только последний сегмент (`zip`);
     * распознавание `fb2.zip` — задача фильтра (`BookFileFilter.detectExtension`).
     */
    val extension: String
        get() {
            val dot = displayName.lastIndexOf('.')
            return if (dot <= 0) "" else displayName.substring(dot + 1).lowercase()
        }

    /**
     * Скрытая запись: имя начинается с точки.
     *
     * Отдельной колонки «скрытый» SAF не отдаёт, поэтому правило совпадает с соглашением
     * файловых систем и с маской `.*` в исключениях источника.
     */
    val isHidden: Boolean
        get() = displayName.startsWith(".")
}
