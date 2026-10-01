package io.github.bookshelf.core.storage.volume

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Какие тома считаются съёмным носителем (`RemovableVolumes.kt`).
 *
 * От этого зависит, появится ли в полках динамическая «Карта памяти»: если считать носителем
 * встроенную память, полка висела бы всегда и означала бы не то, что написано; если пропустить
 * карту без метки — полка не появилась бы вовсе.
 *
 * Проверяются границы: встроенная память не носитель, неподключённый том не носитель, том без
 * UUID не годится (по нему нельзя связать источник — он станет носителем при следующем подключении),
 * а метка системы используется как есть.
 */
class RemovableVolumesTest {

    @Test
    fun `a mounted removable volume is a card`() {
        val volumes = removableVolumesOf(
            listOf(raw(uuid = "1A2B-3C4D", label = "SD-карта", path = "/storage/1A2B-3C4D")),
        )

        assertThat(volumes.map { volume -> volume.uuid }).containsExactly("1A2B-3C4D")
        assertThat(volumes.first().label).isEqualTo("SD-карта")
        assertThat(volumes.first().path).isEqualTo("/storage/1A2B-3C4D")
    }

    @Test
    fun `emulated storage is never a card`() {
        val volumes = removableVolumesOf(
            listOf(raw(uuid = null, label = "Внутренняя память", path = "/storage/emulated/0", emulated = true)),
        )

        assertThat(volumes).isEmpty()
    }

    @Test
    fun `a volume that is not removable is not a card`() {
        val volumes = removableVolumesOf(
            listOf(raw(uuid = "private", label = "Система", path = "/storage/private", removable = false)),
        )

        assertThat(volumes).isEmpty()
    }

    @Test
    fun `an ejected volume is not a card`() {
        val volumes = removableVolumesOf(
            listOf(raw(uuid = "1A2B-3C4D", label = "SD-карта", path = "/storage/1A2B-3C4D", mounted = false)),
        )

        assertThat(volumes).isEmpty()
    }

    @Test
    fun `a volume without a uuid is skipped until it can be matched`() {
        // Без UUID источник не связать с носителем: полка появлялась бы и не исчезала при изъятии.
        val volumes = removableVolumesOf(
            listOf(raw(uuid = null, label = "USB", path = "/storage/usb")),
        )

        assertThat(volumes).isEmpty()
    }

    @Test
    fun `a volume without a path is skipped`() {
        val volumes = removableVolumesOf(
            listOf(raw(uuid = "1A2B-3C4D", label = "SD-карта", path = null)),
        )

        assertThat(volumes).isEmpty()
    }

    @Test
    fun `a blank label is left empty so the screen can name it itself`() {
        val volumes = removableVolumesOf(
            listOf(raw(uuid = "1A2B-3C4D", label = "   ", path = "/storage/1A2B-3C4D")),
        )

        assertThat(volumes).hasSize(1)
        assertThat(volumes.first().label).isEmpty()
    }

    @Test
    fun `several cards are all listed`() {
        val volumes = removableVolumesOf(
            listOf(
                raw(uuid = "1A2B-3C4D", path = "/storage/1A2B-3C4D"),
                raw(uuid = "5E6F-7A8B", path = "/storage/5E6F-7A8B"),
            ),
        )

        assertThat(volumes.map { volume -> volume.uuid })
            .containsExactly("1A2B-3C4D", "5E6F-7A8B")
    }

    private fun raw(
        uuid: String?,
        label: String? = null,
        path: String? = null,
        removable: Boolean = true,
        emulated: Boolean = false,
        mounted: Boolean = true,
    ) = RawVolume(
        uuid = uuid,
        label = label,
        path = path,
        isRemovable = removable,
        isEmulated = emulated,
        isMounted = mounted,
    )
}
