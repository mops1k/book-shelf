package io.github.bookshelf.core.eink.frontlight

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

/**
 * Подсветка через sysfs — то, что приложение может сделать само, без вендорского SDK.
 *
 * Проверяется на настоящих файлах: узлы подсветки — это файлы, и подменять их фейковым
 * «файловым API» значило бы проверять не то, что работает на устройстве.
 *
 * На Bigme B6 Color приложение НЕ видит узлов (`/sys/class/leds` не читается, `/sys/class/backlight`
 * нет вовсе) — проверено инструментальным тестом 01.10.2026. Поэтому главное свойство реализации:
 * отсутствие доступа — это «подсветка не поддерживается», а не падение.
 */
class SysfsFrontLightControllerTest {

    @Test
    fun `a device without light nodes does not support the front light`() {
        val controller = SysfsFrontLightController(candidates = listOf(File("/нет/такого/узла")))

        assertThat(controller.supportsFrontLight()).isFalse()
        assertThat(controller.level()).isNull()
        // Установка уровня на устройстве без подсветки — тихий no-op, а не исключение.
        controller.setLevel(50)
    }

    @Test
    fun `the level is written as a share of the maximum`() {
        val node = writableNode(max = 255)

        val controller = SysfsFrontLightController(candidates = listOf(node.brightness))

        assertThat(controller.supportsFrontLight()).isTrue()
        controller.setLevel(50)
        assertThat(node.brightness.readText().trim()).isEqualTo("128")
    }

    @Test
    fun `the written level is read back`() {
        val node = writableNode(max = 100)
        val controller = SysfsFrontLightController(candidates = listOf(node.brightness))

        controller.setLevel(42)

        assertThat(controller.level()).isEqualTo(42)
    }

    @Test
    fun `a level outside the range is clamped`() {
        val node = writableNode(max = 100)
        val controller = SysfsFrontLightController(candidates = listOf(node.brightness))

        controller.setLevel(150)
        assertThat(node.brightness.readText().trim()).isEqualTo("100")

        controller.setLevel(-5)
        assertThat(node.brightness.readText().trim()).isEqualTo("0")
    }

    @Test
    fun `a node without maximum is read as percent`() {
        // У части прошивок рядом с `brightness` нет `max_brightness`: тогда значение уже в процентах.
        val node = writableNode(max = null)
        val controller = SysfsFrontLightController(candidates = listOf(node.brightness))

        controller.setLevel(30)

        assertThat(node.brightness.readText().trim()).isEqualTo("30")
        assertThat(controller.level()).isEqualTo(30)
    }

    @Test
    fun `a read-only node is not used`() {
        val dir = Files.createTempDirectory("frontlight").toFile()
        val readOnly = File(dir, "brightness").apply {
            writeText("10")
            setReadOnly()
        }

        val controller = SysfsFrontLightController(candidates = listOf(readOnly))

        // Строка настроек не должна появляться там, где приложение всё равно не может записать.
        assertThat(controller.supportsFrontLight()).isFalse()
    }

    @Test
    fun `a broken node value does not break reading`() {
        val node = writableNode(max = null)
        node.brightness.writeText("не число")

        val controller = SysfsFrontLightController(candidates = listOf(node.brightness))

        assertThat(controller.level()).isNull()
    }

    /** Узел в временной папке: `brightness` и, если нужен, `max_brightness` рядом. */
    private fun writableNode(max: Int?): Node {
        val dir = Files.createTempDirectory("frontlight").toFile()
        val brightness = File(dir, "brightness").apply { writeText("0") }
        if (max != null) File(dir, "max_brightness").writeText(max.toString())
        return Node(brightness = brightness)
    }

    private data class Node(val brightness: File)
}
