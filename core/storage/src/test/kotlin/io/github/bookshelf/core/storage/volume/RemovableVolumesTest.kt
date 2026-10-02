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
    fun `a volume the monitor could not reach is not a card`() {
        // Монитор отдаёт путь только для доступного тома: изъятый (или неопубликованный системой,
        // как на Bigme B6 Color) приходит без пути — и носителем не считается.
        val volumes = removableVolumesOf(
            listOf(raw(uuid = "1A2B-3C4D", label = "SD-карта", path = null)),
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
    fun `the public path is preferred and the private one is the fallback`() {
        val candidates = volumePathCandidates(uuid = "1A2B-3C4D", publicPath = "/storage/1A2B-3C4D")

        assertThat(candidates)
            .containsExactly("/storage/1A2B-3C4D", "/mnt/media_rw/1A2B-3C4D")
            .inOrder()
    }

    @Test
    fun `without a public path the private one is offered`() {
        // Так выглядит прошивка, которая монтирует том только приватно (Bigme B6 Color, 02.10.2026).
        assertThat(volumePathCandidates(uuid = "0436-C297", publicPath = null))
            .containsExactly("/mnt/media_rw/0436-C297")
    }

    @Test
    fun `the same path is not offered twice`() {
        assertThat(volumePathCandidates(uuid = "1A2B-3C4D", publicPath = "/mnt/media_rw/1A2B-3C4D"))
            .containsExactly("/mnt/media_rw/1A2B-3C4D")
    }

    @Test
    fun `a volume without uuid keeps only the public path`() {
        // Приватный путь строится из UUID: без него угадывать нечего.
        assertThat(volumePathCandidates(uuid = null, publicPath = "/storage/1A2B-3C4D"))
            .containsExactly("/storage/1A2B-3C4D")
        assertThat(volumePathCandidates(uuid = "  ", publicPath = "/storage/1A2B-3C4D"))
            .containsExactly("/storage/1A2B-3C4D")
    }

    @Test
    fun `the first available path wins`() {
        val candidates = listOf("/storage/1A2B-3C4D", "/mnt/media_rw/1A2B-3C4D")

        assertThat(availableVolumePath(candidates) { path -> path.startsWith("/mnt/") })
            .isEqualTo("/mnt/media_rw/1A2B-3C4D")
        assertThat(availableVolumePath(candidates) { false }).isNull()
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
    ) = RawVolume(
        uuid = uuid,
        label = label,
        path = path,
        isRemovable = removable,
        isEmulated = emulated,
    )
}
