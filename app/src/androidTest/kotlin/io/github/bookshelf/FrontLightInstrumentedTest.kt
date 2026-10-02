package io.github.bookshelf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.eink.frontlight.SysfsFrontLightController
import io.github.bookshelf.core.eink.noop.NoopEinkController
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Подсветка на живом устройстве: приложение либо действительно управляет ею, либо честно
 * говорит «нет» — но не падает и не показывает строку настроек, которая ничего не делает.
 *
 * Замер на Bigme B6 Color (01.10.2026, Android 14, ядро 4.19.191): каталога `/sys/class/backlight`
 * нет вовсе, `/sys/class/leds` приложению не читается (SELinux) — подсветка недоступна, значит
 * `frontLight()` обязан вернуть `null`. На устройстве с открытым узлом проверка пройдёт так же:
 * тогда `frontLight()` вернёт уровень, и тест это подтвердит отдельной веткой.
 */
@RunWith(AndroidJUnit4::class)
class FrontLightInstrumentedTest {

    @Test
    fun the_controller_tells_the_truth_about_the_front_light() {
        val sysfs = SysfsFrontLightController()
        val controller = NoopEinkController(frontLight = sysfs)

        val supported = sysfs.supportsFrontLight()
        val level = controller.frontLight()

        if (supported) {
            // Узел открыт: уровень читается, установка не бросает.
            controller.setFrontLight(50)
            assertThat(controller.frontLight()).isNotNull()
        } else {
            // Узла нет: «нет подсветки» — состояние, а не ошибка; строка настроек не показывается.
            assertThat(level).isNull()
            controller.setFrontLight(50)
        }
    }
}
