package io.github.bookshelf.core.storage.saf

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Адрес папки, в которой лежит документ SAF.
 *
 * Нужен команде «Показать в папке»: система умеет открывать папку документа, если дать ей адрес
 * самой папки. Адрес строится из `documentUri` книги — дерево и идентификатор документа в нём уже
 * есть, поэтому источник (`sources.uri`) для этого не нужен.
 *
 * `null` означает «папку не вычислить»: адрес не от SAF-документа, провайдер не отдал
 * идентификаторы, или документ лежит в корне дерева и его родитель — само дерево.
 */
fun parentFolderUri(documentUri: Uri): Uri? = runCatching {
    val documentId = DocumentsContract.getDocumentId(documentUri)
    val treeId = DocumentsContract.getTreeDocumentId(documentUri)
    val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
    // Верхний уровень дерева: родителя в виде «папка/папка» нет, но адрес корня у дерева есть.
    val targetId = parentId.ifEmpty { treeId }
    DocumentsContract.buildDocumentUriUsingTree(documentUri, targetId)
}.getOrNull()
