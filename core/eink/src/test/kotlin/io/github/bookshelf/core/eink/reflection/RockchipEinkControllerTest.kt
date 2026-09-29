package io.github.bookshelf.core.eink.reflection

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.eink.RefreshMode
import io.github.bookshelf.core.eink.device.EinkDeviceInfo
import io.github.bookshelf.core.eink.device.EinkScreen
import io.github.bookshelf.core.eink.vendor.EinkVendor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Контроллер Rockchip/Boyue до реализации (Sprint 10).
 *
 * Здесь дефект был опаснее, чем у Onyx: `isReflectionAvailable()` на настоящем Rockchip-устройстве
 * возвращает `true` (скрытый метод `View.setByEinkUpdateMode` существует), DI выбирал этот
 * контроллер — и любой вызов бросал `NotImplementedError`. Проверяется, что заглушка не бросает и
 * не заявляет команд, которых не умеет.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class RockchipEinkControllerTest {

    private val controller = RockchipEinkController(device())

    @Test
    fun `no call throws while the implementation is pending`() {
        val view = View(ApplicationProvider.getApplicationContext<Context>())

        controller.setRefreshMode(view, RefreshMode.NORMAL)
        controller.fullRefresh(view)
        controller.setGcInterval(partialUpdates = 5)
        controller.setFrontLight(level = 3)

        assertThat(controller.frontLight()).isNull()
    }

    @Test
    fun `unimplemented controller does not claim vendor commands`() {
        assertThat(controller.hasVendorCommands).isFalse()
        assertThat(controller.isEinkDevice).isTrue()
        assertThat(controller.vendor).isEqualTo(EinkVendor.ROCKCHIP_BOYUE)
    }

    @Test
    fun `waveform codes match the documented Rockchip values`() {
        // Коды сверены с KOReader: путать их нельзя — другой код даёт другую волну обновления.
        assertThat(RockchipEinkController.waveformFor(RefreshMode.NORMAL))
            .isEqualTo(RockchipWaveform.EPD_PART)
        assertThat(RockchipEinkController.waveformFor(RefreshMode.FAST))
            .isEqualTo(RockchipWaveform.EPD_A2)
        assertThat(RockchipEinkController.waveformFor(RefreshMode.FULL))
            .isEqualTo(RockchipWaveform.EPD_FORCE_FULL)
    }

    private fun device() = EinkDeviceInfo(
        isEink = true,
        vendor = EinkVendor.ROCKCHIP_BOYUE,
        screen = EinkScreen.EINK_MONOCHROME,
        model = "Likebook",
        androidSdk = 30,
    )
}
