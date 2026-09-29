package io.github.bookshelf.core.eink.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Определение типа экрана (docs/eink-ux.md §8).
 *
 * Проверяются наборы признаков, снятые с живых устройств, а не придуманные: Bigme B6 Color
 * (`ro.build.ProductCode=COLOR`, имя «B6 Color», `Build.MANUFACTURER=Bigme`) и обычный телефон.
 *
 * Главное, что защищается тестами: устройство с электронной бумагой не должно называться обычным
 * экраном только потому, что у вендора нет контроллера обновления, — именно на этом diagnostic
 * врал на Bigme.
 */
class EinkScreenDetectionTest {

    @Test
    fun `bigme colour paper is detected as colour`() {
        val screen = detectScreen(
            vendor = io.github.bookshelf.core.eink.vendor.EinkVendor.BIGME,
            manufacturer = "Bigme",
            brand = "Bigme",
            model = "HiBreak",
            systemProperties = BIGME_COLOR_PROPERTIES,
        )

        assertThat(screen).isEqualTo(EinkScreen.EINK_COLOR)
    }

    @Test
    fun `bigme without colour markers is monochrome paper`() {
        val screen = detectScreen(
            vendor = io.github.bookshelf.core.eink.vendor.EinkVendor.BIGME,
            manufacturer = "Bigme",
            brand = "Bigme",
            model = "HiBreak",
            systemProperties = BIGME_PROPERTIES,
        )

        assertThat(screen).isEqualTo(EinkScreen.EINK_MONOCHROME)
    }

    @Test
    fun `onyx paper is monochrome`() {
        val screen = detectScreen(
            vendor = io.github.bookshelf.core.eink.vendor.EinkVendor.ONYX,
            manufacturer = "Onyx",
            brand = "onyx",
            model = "BOOX Note Air",
            systemProperties = emptyMap(),
        )

        assertThat(screen).isEqualTo(EinkScreen.EINK_MONOCHROME)
    }

    @Test
    fun `phone is a normal screen`() {
        val screen = detectScreen(
            vendor = io.github.bookshelf.core.eink.vendor.EinkVendor.UNKNOWN,
            manufacturer = "Samsung",
            brand = "samsung",
            model = "SM-G991B",
            systemProperties = mapOf("ro.product.model" to "SM-G991B"),
        )

        assertThat(screen).isEqualTo(EinkScreen.NORMAL)
    }

    @Test
    fun `unknown vendor with paper markers is honest about it`() {
        // Вендорские признаки бумаги есть (`vendor.xrz.*`), а имени производителя в них нет:
        // назвать это чёрно-белой бумагой было бы выдумкой, обычным экраном — тоже.
        val screen = detectScreen(
            vendor = io.github.bookshelf.core.eink.vendor.EinkVendor.UNKNOWN,
            manufacturer = "XrzTech",
            brand = null,
            model = "Unknown Reader",
            systemProperties = mapOf("vendor.xrz.disable_eink_vsync" to "0"),
        )

        assertThat(screen).isEqualTo(EinkScreen.EINK_UNKNOWN)
    }

    @Test
    fun `colour marker is found in the model name too`() {
        val screen = detectScreen(
            vendor = io.github.bookshelf.core.eink.vendor.EinkVendor.BIGME,
            manufacturer = "Bigme",
            brand = null,
            model = "B6 Color",
            systemProperties = emptyMap(),
        )

        assertThat(screen).isEqualTo(EinkScreen.EINK_COLOR)
    }

    private companion object {
        /** Снято с Bigme B6 Color: цветной экран. */
        val BIGME_COLOR_PROPERTIES: Map<String, String> = mapOf(
            "ro.build.ProductCode" to "COLOR",
            "bluetooth.device.default_name" to "B6 Color",
            "ro.build.fingerprint" to "Bigme/HiBreak/Smartphone:14/UP1A.231005.007/1739236620:user/dev-keys",
            "persist.sys.hctmodel" to "Bigme",
        )

        /** Тот же вендор, но без признаков цвета. */
        val BIGME_PROPERTIES: Map<String, String> = mapOf(
            "ro.build.fingerprint" to "Bigme/HiBreak/Smartphone:14/UP1A.231005.007/1739236620:user/dev-keys",
            "persist.sys.hctmodel" to "Bigme",
        )
    }
}
