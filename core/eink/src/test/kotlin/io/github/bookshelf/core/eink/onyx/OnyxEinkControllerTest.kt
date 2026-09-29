package io.github.bookshelf.core.eink.onyx

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
 * Контроллер Onyx до реализации (Sprint 10).
 *
 * Дефект, ради которого это появилось: методы бросали `NotImplementedError`. На устройстве, где
 * вендорский признак распознан, контроллер выбирается DI — и первое же обращение (смена режима,
 * полный refresh, подсветка) роняло экран. Заглушка обязана молча ничего не делать и честно
 * говорить, что команд вендора у неё нет.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class OnyxEinkControllerTest {

    private val controller = OnyxEinkController(device())

    @Test
    fun `no call throws while the implementation is pending`() {
        val view = View(ApplicationProvider.getApplicationContext<Context>())

        controller.setRefreshMode(view, RefreshMode.FAST)
        controller.fullRefresh(view)
        controller.setGcInterval(partialUpdates = 10)
        controller.setFrontLight(level = 5)

        // Уровень подсветки заглушка не знает и обязана сказать об этом, а не выдумывать число.
        assertThat(controller.frontLight()).isNull()
    }

    @Test
    fun `unimplemented controller does not claim vendor commands`() {
        assertThat(controller.hasVendorCommands).isFalse()
        // Что устройство определено верно — важно: иначе диагностика соврала бы про железо.
        assertThat(controller.isEinkDevice).isTrue()
        assertThat(controller.vendor).isEqualTo(EinkVendor.ONYX)
    }

    @Test
    fun `sdk is not claimed to be available before it is verified`() {
        // `true` здесь означал бы выбор контроллера, который ничего не умеет.
        assertThat(OnyxEinkController.isSdkAvailable()).isFalse()
    }

    private fun device() = EinkDeviceInfo(
        isEink = true,
        vendor = EinkVendor.ONYX,
        screen = EinkScreen.EINK_MONOCHROME,
        model = "Boox",
        androidSdk = 30,
    )
}
