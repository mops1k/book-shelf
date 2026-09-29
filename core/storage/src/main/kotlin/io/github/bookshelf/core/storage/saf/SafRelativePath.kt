package io.github.bookshelf.core.storage.saf

/**
 * Путь документа относительно корня выбранного дерева SAF.
 *
 * У `DocumentEntry` есть только [DocumentEntry.displayName] — имя файла без папок, а `documentId`
 * SAF отдаёт полным, вместе с идентификатором дерева (`primary:Books/Морган Ричард К./файл.epub`).
 * Для карточки книги и для группировки «По папкам» нужен как раз относительный путь, поэтому
 * идентификатор дерева отбрасывается.
 *
 * Почему отдельная функция, а не строка в воркере скана: путь собирается из двух значений, пришедших
 * из разных мест (корень дерева и документ), и ошибка здесь не падает, а тихо портит путь —
 * именно так 66 книг на устройстве получили путь из одного имени файла. Здесь это проверяется
 * тестом без Android.
 *
 * @param treeDocumentId корень дерева (`DocumentsContract.getTreeDocumentId`).
 * @param documentId идентификатор документа; у корня совпадает с [treeDocumentId].
 * @return путь без ведущего слэша; пустая строка для корня.
 */
fun relativePathInTree(treeDocumentId: String, documentId: String): String {
    val root = treeDocumentId.trimEnd('/')
    if (documentId == root) return ""
    val prefix = "$root/"
    if (documentId.startsWith(prefix)) return documentId.removePrefix(prefix)
    // Провайдер вернул документ из другого дерева или идентификатор без общего префикса: полный
    // идентификатор в пути был бы хуже, чем имя файла, поэтому берём последний сегмент.
    return documentId.substringAfterLast('/')
}
