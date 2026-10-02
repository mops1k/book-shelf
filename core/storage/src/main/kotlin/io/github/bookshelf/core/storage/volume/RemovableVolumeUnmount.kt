package io.github.bookshelf.core.storage.volume

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Интент системных настроек памяти — оттуда человек извлекает съёмный носитель.
 *
 * Публичного способа размонтировать том у приложения нет: `StorageVolume.createUnmountIntent()` в
 * публичном SDK отсутствует (проверено сборкой на compileSdk 35, 02.10.2026), а скрытый API на
 * Android 14 заблокирован. Поэтому кнопка «Отключить» открывает системный экран, где изъятие делает
 * сама система — это работает на любой прошивке, ценой второго шага.
 *
 * @return интент для запуска экраном либо `null`, если на устройстве нет обработчика.
 */
fun storageSettingsIntentOf(context: Context): Intent? =
    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        .takeIf { intent -> intent.resolveActivity(context.packageManager) != null }
