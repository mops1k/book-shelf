package io.github.bookshelf.core.storage.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Носители из системы: `StorageManager` плюс широковещательные события о медиа.
 *
 * Почему события, а не `registerStorageVolumeCallback`: обратный вызов появился только в API 30,
 * а приложение поддерживает 24. События `ACTION_MEDIA_*` есть везде и приходят с путём тома
 * в данных, поэтому обновление списка не зависит от версии системы.
 *
 * Путь тома: на API 30+ его отдаёт `StorageVolume.getDirectory()`, а на 24–29 — стандартная точка
 * монтирования `/storage/<uuid>`. Скрытый `getPath()` не используется: он не публичный и на части
 * прошивок отсутствует.
 */
class AndroidRemovableVolumeMonitor(
    private val context: Context,
) : RemovableVolumeMonitor {

    override fun observe(): Flow<List<RemovableVolume>> = callbackFlow {
        // Первое значение — до событий: подписка должна видеть носитель, вставленный до запуска.
        trySend(current())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme(FILE_SCHEME)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Носители на момент вызова: событие лишь повод перечитать список целиком. */
    private fun current(): List<RemovableVolume> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return removableVolumesOf(manager.storageVolumes.map { volume -> volume.toRaw() })
    }

    private fun StorageVolume.toRaw(): RawVolume {
        val volumeUuid = uuid?.trim()
        val path = directoryPath(volumeUuid)
        return RawVolume(
            uuid = volumeUuid,
            label = runCatching { getDescription(context) }.getOrNull(),
            path = path,
            isRemovable = isRemovable,
            isEmulated = isEmulated,
            // `StorageVolume.getState()` есть только с API 30, а состояние нужно и на 24: его
            // спрашивает `Environment` по пути тома.
            isMounted = path != null &&
                Environment.getExternalStorageState(File(path)) == Environment.MEDIA_MOUNTED,
        )
    }

    private fun StorageVolume.directoryPath(volumeUuid: String?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return directory?.absolutePath
        }
        return volumeUuid?.let { uuid -> "$MOUNT_ROOT/$uuid" }
    }

    private companion object {
        const val FILE_SCHEME = "file"
        const val MOUNT_ROOT = "/storage"
    }
}
