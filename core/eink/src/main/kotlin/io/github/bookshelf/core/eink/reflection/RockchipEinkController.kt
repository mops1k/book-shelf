package io.github.bookshelf.core.eink.reflection

import android.view.View
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.RefreshMode
import io.github.bookshelf.core.eink.device.EinkDeviceInfo
import io.github.bookshelf.core.eink.vendor.EinkVendor

/**
 * Коды waveform для Rockchip-контроллеров (docs/eink-ux.md §8.2).
 *
 * Совпадают с константами, которые использует KOReader в `org.koreader.launcher.device.epd`.
 * Передаются в скрытые методы `View` (см. [RockchipEinkController]).
 */
object RockchipWaveform {
    const val EPD_A2 = 2
    const val EPD_PART = 3
    const val EPD_BLACK_WHITE = 6
    const val EPD_FORCE_FULL = 11
    const val EPD_REGAL = 15
    const val EPD_ADAPTATIVE = 17
    const val EPD_FAST = 18
}

/**
 * Контроллер для Rockchip-клонов: Meebook / Boyue / Likebook (docs/eink-ux.md §8.2, ADR-009).
 *
 * Публичного SDK у этих устройств нет. Проверенный путь — тот же, что у KOReader
 * (`android-luajit-launcher`, пакет `org.koreader.launcher.device.epd`): рефлексия по **скрытым
 * методам `android.view.View`**:
 * * `setByEinkUpdateMode(int)` — Rockchip-клоны, включая Likebook Mimas/Mars/Muses;
 * * `setWaveformAndScheme(int, int, int)` и `refreshScreen(x, y, w, h, mode)` — Qualcomm/Onyx;
 * * либо нативный `ioctl` через собственную библиотеку — **не берём**: хрупко и требует NDK.
 *
 * Коды waveform — в [RockchipWaveform] (`EPD_A2 = 2` и т. д.). Именно здесь, а не в Onyx-контроллере,
 * живёт режим `A2` (ADR-009).
 *
 * Деградация: если скрытых методов нет ([isReflectionAvailable] == false), DI создаёт
 * [io.github.bookshelf.core.eink.noop.NoopEinkController].
 *
 * TODO("STUB(Sprint 10)"): реализация вызовов через рефлексию + кэширование `Method`-объектов
 *  (поиск метода в каждом кадре недопустим на e-ink).
 */
class RockchipEinkController(
    private val device: EinkDeviceInfo,
) : EinkController {

    override val isEinkDevice: Boolean = device.isEink

    override val vendor: EinkVendor = EinkVendor.ROCKCHIP_BOYUE

    /** Rockchip-клоны умеют `EPD_A2`/`EPD_FAST` — быстрый режим доступен при наличии методов. */
    override val supportsFastMode: Boolean = true

    override fun setRefreshMode(view: View, mode: RefreshMode) {
        TODO("STUB(Sprint 10): view.setByEinkUpdateMode(rockchipWaveformFor(mode)) через рефлексию")
    }

    override fun fullRefresh(view: View) {
        TODO("STUB(Sprint 10): refreshScreen(...) с EPD_FORCE_FULL / EPD_BLACK_WHITE через рефлексию")
    }

    override fun setGcInterval(partialUpdates: Int) {
        TODO("STUB(Sprint 10): у Rockchip нет setGcInterval — счётчик частичных обновлений ведём сами")
    }

    override fun setFrontLight(level: Int) {
        TODO("STUB(Sprint 10): подсветка через sysfs/скрытый API устройства, если доступна")
    }

    override fun frontLight(): Int? {
        TODO("STUB(Sprint 10): чтение уровня подсветки; null, если недоступно")
    }

    companion object {

        /** Скрытый метод Rockchip-клонов, по наличию которого определяется поддержка. */
        private const val SET_BY_EINK_UPDATE_MODE = "setByEinkUpdateMode"

        /**
         * Есть ли на этом устройстве скрытый `View.setByEinkUpdateMode(int)`.
         *
         * Проверка безопасна: отсутствие метода — штатная ситуация, а не ошибка, поэтому исключение
         * рефлексии глушится и превращается в `false`.
         */
        fun isReflectionAvailable(): Boolean = runCatching {
            View::class.java.getDeclaredMethod(SET_BY_EINK_UPDATE_MODE, Integer.TYPE)
        }.isSuccess

        /**
         * Соответствие общего режима и waveform Rockchip.
         *
         * TODO("STUB(Sprint 10)"): использовать после реализации [setRefreshMode];
         *  `NORMAL` → [RockchipWaveform.EPD_PART], `FAST` → [RockchipWaveform.EPD_A2],
         *  `FULL` → [RockchipWaveform.EPD_FORCE_FULL].
         */
        fun waveformFor(mode: RefreshMode): Int = when (mode) {
            RefreshMode.NORMAL -> RockchipWaveform.EPD_PART
            RefreshMode.FAST -> RockchipWaveform.EPD_A2
            RefreshMode.FULL -> RockchipWaveform.EPD_FORCE_FULL
        }
    }
}
