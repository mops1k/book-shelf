package io.github.bookshelf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.RequiresDevice
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bookshelf.core.storage.volume.AndroidRemovableVolumeMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Зонд съёмных томаов на живом устройстве: что отдаёт система и что из этого делает монитор.
 *
 * Проверок здесь нет намеренно: на устройстве без носителя ожидать нечего, а на устройстве с
 * носителем результат зависит от прошивки. Задача зонда — напечатать факты в лог прогона
 * (Bigme B6 Color, 02.10.2026: система монтирует партиции только в /mnt/media_rw/<uuid>).
 *
 * `@RequiresDevice`: зонд нужен на устройстве со съёмным носителем; на эмуляторе виден только
 * встроенный эмулированный том, и напечатанные факты ничего не значат.
 */
@RunWith(AndroidJUnit4::class)
@RequiresDevice
class RemovableVolumesProbeTest {

    @Test
    fun print_what_the_system_reports_and_what_the_monitor_finds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(android.os.storage.StorageManager::class.java)

        val volumes = manager?.storageVolumes.orEmpty()
        println("PROBE: томов у системы ${volumes.size}")
        volumes.forEach { volume ->
            val path = runCatching { volume.directory?.absolutePath }.getOrNull()
            val description = runCatching { volume.getDescription(context) }.getOrNull()
            println(
                "PROBE: uuid=${volume.uuid} removable=${volume.isRemovable} " +
                    "emulated=${volume.isEmulated} state=${volume.state} dir=$path desc=$description",
            )
        }

        volumes.forEach { volume ->
            val uuid = volume.uuid?.trim()
            val publicPath = runCatching { volume.directory?.absolutePath }.getOrNull()
            val candidates = io.github.bookshelf.core.storage.volume.volumePathCandidates(uuid, publicPath)
            println("PROBE: кандидаты uuid=$uuid: $candidates")
            candidates.forEach { candidate ->
                val file = java.io.File(candidate)
                println(
                    "PROBE:   $candidate dir=${file.isDirectory} canRead=${file.canRead()} " +
                        "state=${android.os.Environment.getExternalStorageState(file)}",
                )
            }
            println("PROBE:   appops: ${runCatching { android.app.AppOpsManager::class.java }.isSuccess}")
        }

        val found = runBlocking {
            AndroidRemovableVolumeMonitor(context).observe().first()
        }
        println("PROBE: монитор нашёл носителей ${found.size}")
        found.forEach { volume ->
            println("PROBE: найден uuid=${volume.uuid} label=${volume.label} path=${volume.path}")
        }
    }
}
