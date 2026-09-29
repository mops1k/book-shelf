package io.github.bookshelf.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Настройки внешнего вида, которые дизайн-система обязана уметь применить, но не должна
 * читать сама: `:core:designsystem` не знает про DataStore и не зависит от `:core:datastore`.
 *
 * Значения подставляет `:app` один раз на корне композиции из `SettingsRepository.settings`
 * (см. `MainActivity`). Поэтому смена настройки применяется без перезапуска, а компонентам
 * достаточно прочитать локал — ни одному экрану не нужно прокидывать флаг через параметры.
 *
 * Локалы объявлены как [staticCompositionLocalOf], а не `compositionLocalOf`: настройка меняется
 * только действием пользователя в разделе «Внешний вид», а не каждый кадр, и при её изменении
 * перерисовать дерево целиком — именно то, что нужно. Это тот же выбор, что и у [LocalEinkColors]
 * и [LocalEinkDimens].
 */

/**
 * Инвертировать ли обложки книг.
 *
 * Отдельная настройка, а не следствие тёмной темы: на e-ink цветная обложка в негативе читается
 * лучше, но интерфейс вокруг неё может остаться светлым (docs/eink-ux.md §2.1, §5).
 */
val LocalInvertCovers = staticCompositionLocalOf { false }

/**
 * Листать ли страницы клавишами громкости.
 *
 * По умолчанию `true` — как у [io.github.bookshelf.core.designsystem.input.keyCodeToEinkAction]:
 * значение нужно только там, где локал никто не подставил (тесты, превью, отдельный экран),
 * а приложение всегда подставляет своё значение из настроек.
 */
val LocalVolumeKeysAsPageTurn = staticCompositionLocalOf { true }
