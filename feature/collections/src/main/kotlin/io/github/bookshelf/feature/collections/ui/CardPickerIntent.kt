package io.github.bookshelf.feature.collections.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import io.github.bookshelf.core.data.removable.volumeUuidOf

/**
 * Интент системного выбора папки, открытого на носителе.
 *
 * Порядок утверждён Александром 01.10.2026: сначала платформенный помощник тома
 * ([StorageVolume.createOpenDocumentTreeIntent], API 29+) — он открывает выбор на корне именно
 * этого тома; если том системе неизвестен или версия ниже, остаётся адрес дерева тома
 * (`EXTRA_INITIAL_URI` с API 26); на 24–25 система начальный адрес не понимает вовсе, и выбор
 * откроется там, где помнит.
 *
 * Том ищется по UUID внутри адреса дерева ([volumeUuidOf]): адрес вида
 * `content://…/tree/1A2B-3C4D%3A` содержит его и в экранированном виде.
 */
internal fun folderPickerIntent(context: Context, treeUri: String): Intent {
    val volume = volumeOf(context, treeUri)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volume != null) {
        return volume.createOpenDocumentTreeIntent()
    }
    return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(treeUri))
        }
    }
}

/** Съёмный том с этим UUID или `null`, если система его не знает. */
private fun volumeOf(context: Context, treeUri: String): StorageVolume? {
    val uuid = volumeUuidOf(treeUri) ?: return null
    val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return null
    return manager.storageVolumes.firstOrNull { volume -> volume.uuid.equals(uuid, ignoreCase = true) }
}
