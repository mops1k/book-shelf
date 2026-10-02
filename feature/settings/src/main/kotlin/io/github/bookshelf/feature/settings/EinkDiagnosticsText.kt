package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.eink.device.EinkScreen

/**
 * Подпись типа экрана для «О приложении».
 *
 * Живёт в модуле настроек, а не в `:core:eink`: у ядра нет ресурсов, и готовая русская строка
 * оттуда оставалась русской в английском интерфейсе. Чистая функция без `Context` — сравнивается
 * с ресурсом в JUnit («TDD» §3).
 */
internal fun EinkScreen.text(): AppText = when (this) {
    EinkScreen.NORMAL -> AppText(R.string.settings_screen_normal)
    EinkScreen.EINK_COLOR -> AppText(R.string.settings_screen_eink_color)
    EinkScreen.EINK_MONOCHROME -> AppText(R.string.settings_screen_eink_mono)
    EinkScreen.EINK_UNKNOWN -> AppText(R.string.settings_screen_eink_unknown)
}
