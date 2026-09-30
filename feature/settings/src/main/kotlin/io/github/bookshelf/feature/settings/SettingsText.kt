package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.designsystem.text.AppText

/**
 * Текст для человека в настройках — общий тип приложения.
 *
 * Псевдоним, а не свой тип: правило «текст рождается вне Composable, показывается на экране» одно
 * на всё приложение, и второй такой же класс только разошёлся бы с первым.
 */
internal typealias SettingsText = AppText
