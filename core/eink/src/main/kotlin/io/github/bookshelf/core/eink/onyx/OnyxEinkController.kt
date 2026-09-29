package io.github.bookshelf.core.eink.onyx

import android.view.View
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.RefreshMode
import io.github.bookshelf.core.eink.device.EinkDeviceInfo
import io.github.bookshelf.core.eink.noop.NoopEinkController
import io.github.bookshelf.core.eink.vendor.EinkVendor

/**
 * Контроллер для устройств Onyx Boox через публичный Onyx SDK (docs/eink-ux.md §8.1, ADR-009).
 *
 * **Параметры SDK**
 * * Maven-репозиторий: `http://repo.boox.com/repository/maven-public/` (только `http`: https-эндпоинт
 *   отдаёт DH-ключ, который JVM отвергает — см. `settings.gradle.kts`);
 * * groupId `com.onyx.android.sdk`, артефакты `onyxsdk-device` 1.3.6 и `onyxsdk-base` 1.8.7;
 * * лицензия BSD-3-Clause (по POM); демо-репозиторий — Apache-2.0.
 *
 * **Как подключается.** Только `compileOnly` в `:core:eink` + рефлексия в рантайме: SDK не должен
 * становиться обязательной зависимостью и не должен ломать сборку на чужих устройствах.
 * Если классы SDK не найдены ([isSdkAvailable] == false), [io.github.bookshelf.core.eink.di.EinkModule]
 * создаёт [NoopEinkController] — деградация вместо падения.
 *
 * **Какие вызовы нужны (точные имена — по исходникам SDK)**
 * * `EpdDeviceManager.setGcInterval(n)` — главный инструмент против ghosting: полный GC каждые
 *   `n` частичных обновлений (см. [setGcInterval]);
 * * `EpdDeviceManager.applyGCUpdate(view)`, `applyWithGCIntervalWithoutRegal(view)`,
 *   `applyWithGCIntervalWitRegal(view, flag)` — полный refresh с учётом интервала (см. [fullRefresh]);
 * * `EpdController.repaintEveryThing(UpdateMode)` — принудительный полный перерисон;
 * * `EpdController.setViewDefaultUpdateMode(view, UpdateMode)` и `invalidate(view, UpdateMode)` —
 *   режим конкретного `View` (см. [setRefreshMode]);
 * * `Device.currentDevice().setAppScopeRefreshMode(UpdateOption)` / `getAppScopeRefreshMode()` —
 *   режим уровня приложения.
 *
 * **Важные уточнения, зафиксированные в ADR-009, чтобы не повторять ошибку:**
 * * класса `ViewUpdateMode` в SDK **нет**: для экрана используется `UpdateMode` (`DU`, `DU_QUALITY`,
 *   `GU`, `GC`, `ANIMATION`, `ANIMATION_QUALITY`, `REGAL`, `REGAL_D`), для приложения — `UpdateOption`
 *   (`NORMAL`, `FAST_QUALITY`, `REGAL`, `FAST`, `FAST_X`);
 * * режим **`A2` к Onyx не относится** — он живёт в Rockchip-контроллерах Boyue/Meebook
 *   ([io.github.bookshelf.core.eink.reflection.RockchipEinkController]);
 * * `supportFastMode` = true соответствует `UpdateOption.FAST`/`FAST_X`, а не «A2».
 *
 * **Состояние.** Вызовы SDK ещё не реализованы (Sprint 10): методы ниже ничего не делают, а
 * [hasVendorCommands] равно `false`, поэтому приложение ведёт себя как на устройстве без вендорских
 * команд. Бросать здесь нельзя: контроллер выбирается DI по вендору, и исключение из метода
 * обновления роняло бы экран (дефект найден 29.09.2026).
 */
class OnyxEinkController(
    private val device: EinkDeviceInfo,
) : EinkController {

    override val isEinkDevice: Boolean = device.isEink

    /**
     * Команд вендора пока нет: реализация Sprint 10.
     *
     * `false` здесь принципиально: приложение по этому признаку выбирает общее поведение, а не
     * зовёт методы, которых нет. Раньше признак был `true`, а методы бросали
     * `NotImplementedError` — на распознанном устройстве это роняло экран при первом обновлении.
     */
    override val hasVendorCommands: Boolean = false

    override val vendor: EinkVendor = EinkVendor.ONYX

    /** Onyx даёт уровни приложения `FAST`/`FAST_X` — «быстрый» режим доступен. */
    override val supportsFastMode: Boolean = true

    override fun setRefreshMode(view: View, mode: RefreshMode) {
        // Реализация Sprint 10 (режимы вендора). До неё — молча ничего: падение здесь означало бы
        // нерабочее приложение на распознанном устройстве.
    }

    override fun fullRefresh(view: View) {
        // Реализация Sprint 10; до неё кадр обновляет сама система, как на обычном экране.
        view.invalidate()
    }

    override fun setGcInterval(partialUpdates: Int) {
        // Реализация Sprint 10; счётчик частичных обновлений ведётся в приложении, не здесь.
    }

    override fun setFrontLight(level: Int) {
        // Реализация Sprint 10: подсветка доступна не на каждой прошивке.
    }

    override fun frontLight(): Int? {
        // `null` — «уровень неизвестен»: выдуманное число врало бы человеку в настройках.
        return null
    }

    companion object {

        /**
         * Доступен ли Onyx SDK в рантайме.
         *
         * TODO("STUB(Sprint 10)"): проверка `Class.forName` по точным FQN классов SDK
         *  (`EpdDeviceManager`/`EpdController`/`Device`) после сверки с артефактом `onyxsdk-device`.
         *  Пока возвращает `false`: это гарантирует безопасную деградацию к [NoopEinkController]
         *  и не даёт вызывать непроверенные методы SDK.
         */
        @Suppress("FunctionOnlyReturningConstant")
        fun isSdkAvailable(): Boolean = false
    }
}
