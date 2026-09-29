package io.github.bookshelf.core.eink.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.device.AndroidEinkDeviceDetector
import io.github.bookshelf.core.eink.device.EinkDeviceDetector
import io.github.bookshelf.core.eink.device.EinkDeviceInfo
import io.github.bookshelf.core.eink.noop.NoopEinkController
import io.github.bookshelf.core.eink.onyx.OnyxEinkController
import io.github.bookshelf.core.eink.reflection.RockchipEinkController
import io.github.bookshelf.core.eink.vendor.EinkVendor
import javax.inject.Singleton

/**
 * DI-обвязка e-ink слоя (docs/architecture.md §6, ADR-009).
 *
 * Детектор определяет вендора один раз при старте, контроллер выбирается по вендору. Сами
 * контроллеры — заглушки Sprint 10, поэтому выбор оформлен через `when`, а не через динамический
 * поиск реализации: DI-граф должен быть явным, а не «магическим».
 *
 * Деградация обязательна: если вендорский API недоступен (или вендор не распознан), возвращается
 * [NoopEinkController] — приложение работает на любом устройстве без вреда.
 */
@Module
@InstallIn(SingletonComponent::class)
object EinkModule {

    @Provides
    @Singleton
    fun provideEinkDeviceDetector(): EinkDeviceDetector = AndroidEinkDeviceDetector()

    @Provides
    @Singleton
    fun provideEinkController(detector: EinkDeviceDetector): EinkController {
        val device: EinkDeviceInfo = detector.detect()
        return when (device.vendor) {
            EinkVendor.ONYX ->
                if (OnyxEinkController.isSdkAvailable()) {
                    OnyxEinkController(device)
                } else {
                    // SDK нет в рантайме — работаем как на обычном устройстве.
                    NoopEinkController(device)
                }

            EinkVendor.ROCKCHIP_BOYUE ->
                if (RockchipEinkController.isReflectionAvailable()) {
                    RockchipEinkController(device)
                } else {
                    NoopEinkController(device)
                }

            EinkVendor.HISENSE,
            EinkVendor.TOLINO,
            EinkVendor.BIGME,
            EinkVendor.DASUNG,
            EinkVendor.POCKETBOOK,
            EinkVendor.IREADER,
            EinkVendor.INKPALM,
            EinkVendor.UNKNOWN,
            -> NoopEinkController(device)
        }
    }
}
