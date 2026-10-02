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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // Перечитать список ещё раз с небольшой задержкой: система убирает том из своего списка не
        // мгновенно, и колбэк приходит на снятие ПЕРВОГО тома, когда второй ещё числится подключённым.
        // Без перечитки на экране оставалась строка уже вынутого носителя (наблюдение Александра
        // 02.10.2026: из двух томов пропал один).
        fun refresh() {
            trySend(current())
            launch {
                delay(SETTLE_DELAY_MS)
                trySend(current())
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                refresh()
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

        // Страховка: на проверенной прошивке ни legacy-рассылки, ни колбэк системы не приходят ни на
        // вставку, ни на изъятие (Bigme B6 Color, 02.10.2026: тома появлялись только после перехода
        // между экранами). Пока подписка жива, список перечитывается сам — экран открыт, значит
        // состояние обязано быть верным.
        launch {
            while (true) {
                delay(POLL_DELAY_MS)
                trySend(current())
            }
        }

        // Современный путь: система сама сообщает о смене состояния тома (`StorageVolumeCallback`,
        // API 30+). Legacy-рассылки `MEDIA_*` для SD/USB прошивки часто не отправляют вовсе —
        // проверено на Bigme B6 Color 02.10.2026: изъятие не обновляло список полок, пока экран не
        // пересоздавали. Без этого колбэка строка изъятого носителя остаётся на экране.
        val storageManager = context.getSystemService(StorageManager::class.java)
        val volumeCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager?.let { manager ->
                object : StorageManager.StorageVolumeCallback() {
                    override fun onStateChanged(volume: StorageVolume) {
                        refresh()
                    }
                }.also { registered ->
                    manager.registerStorageVolumeCallback(
                        ContextCompat.getMainExecutor(context),
                        registered,
                    )
                }
            }
        } else {
            null
        }

        awaitClose {
            volumeCallback?.let { registered ->
                storageManager?.unregisterStorageVolumeCallback(registered)
            }
            context.unregisterReceiver(receiver)
        }
    }

    /** Носители на момент вызова: событие лишь повод перечитать список целиком. */
    private fun current(): List<RemovableVolume> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return removableVolumesOf(manager.storageVolumes.map { volume -> volume.toRaw() })
    }

    private fun StorageVolume.toRaw(): RawVolume {
        val volumeUuid = uuid?.trim()
        return RawVolume(
            uuid = volumeUuid,
            label = runCatching { getDescription(context) }.getOrNull(),
            path = availablePath(volumeUuid),
            isRemovable = isRemovable,
            isEmulated = isEmulated,
        )
    }

    /**
     * Путь, по которому том читается сейчас, или `null`, если такого пути нет.
     *
     * Состояние публичного пути спрашивается у `Environment` (`StorageVolume.getState()` есть
     * только с API 30, а состояние нужно и на 24). Приватный путь читает только приложение с
     * доступом ко всем файлам, и `Environment` про такой путь ответит «нет тома» — поэтому кроме
     * состояния проверяется читаемость каталога. Без доступа ко всем файлам приватный кандидат не
     * откроется, и том останется незамеченным: полки без читаемого тома быть не должно.
     */
    private fun StorageVolume.availablePath(volumeUuid: String?): String? =
        availableVolumePath(volumePathCandidates(volumeUuid, directoryPath(volumeUuid))) { candidate ->
            // Путь годится, если система считает его подключённым (`Environment` знает публичные
            // точки вроде `/storage/<uuid>`) ИЛИ если каталог существует и читается: так видна
            // приватная точка `/mnt/media_rw/<uuid>`, о которой `Environment` ничего не знает и
            // отвечает `unknown`. Проверять «какой это путь» нельзя: на проверенном устройстве
            // `StorageVolume.directory` сам равен приватному пути, и признак «публичный» выбрасывал
            // бы том целиком (Bigme B6 Color, 02.10.2026 — зонд `RemovableVolumesProbeTest`).
            Environment.getExternalStorageState(File(candidate)) == Environment.MEDIA_MOUNTED ||
                File(candidate).let { directory -> directory.isDirectory && directory.canRead() }
        }

    private fun StorageVolume.directoryPath(volumeUuid: String?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return directory?.absolutePath
        }
        return volumeUuid?.let { uuid -> "$MOUNT_ROOT/$uuid" }
    }

    private companion object {
        /** Пауза перед повторным чтением списка томов: система успевает закончить снятие. */
        const val SETTLE_DELAY_MS = 1_000L

        /** Период страховочного опроса списка томов, пока экран открыт. */
        const val POLL_DELAY_MS = 2_000L

        const val FILE_SCHEME = "file"
        const val MOUNT_ROOT = "/storage"
    }
}
