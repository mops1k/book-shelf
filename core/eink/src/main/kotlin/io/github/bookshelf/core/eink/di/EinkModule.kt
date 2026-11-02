package io.github.bookshelf.core.eink.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.eink.device.AndroidEinkDeviceDetector
import io.github.bookshelf.core.eink.device.EinkDeviceDetector
import javax.inject.Singleton

/**
 * DI-обвязка e-ink слоя («Архитектура» §6, ADR-009, ADR-017).
 *
 * Приложению нужен только детектор: он отвечает, что за устройство и какой у него экран, — и этим
 * ограничивается вся диагностика. Управления режимом обновления и подсветкой в приложении больше
 * нет: экраном управляют системные утилиты устройства (ADR-017), поэтому и вендорских контроллеров
 * (Onyx, Rockchip, no-op) здесь тоже нет.
 *
 * Детектор читает `Build.*` и системные свойства один раз, поэтому он синглтон: повторное чтение
 * на каждом открытии настроек ничего бы не изменило, только замедлило экран.
 */
@Module
@InstallIn(SingletonComponent::class)
object EinkModule {

    @Provides
    @Singleton
    fun provideEinkDeviceDetector(): EinkDeviceDetector = AndroidEinkDeviceDetector()
}
