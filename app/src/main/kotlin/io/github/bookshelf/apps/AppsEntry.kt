package io.github.bookshelf.apps

import android.content.Intent

/** Как экран «Приложения» попал на экран (ADR-015, фаза 6). */
enum class AppsEntryMode {
    /** Вкладка нижней панели внутри основного приложения. */
    TAB,

    /** Отдельный запуск: аппаратная кнопка устройства или иконка `AppsAlias` в списке приложений. */
    STANDALONE,
}

/**
 * Точка входа экрана «Приложения».
 *
 * Отдельная exported-активити нужна ради аппаратной кнопки: вендорский список назначения кнопок
 * показывает приложения, а не компоненты, поэтому у BookShelf должен быть собственный пункт
 * запуска. По решению Александра 02.11.2026 вид экрана в обоих случаях одинаковый — та же нижняя
 * панель «Полка | Приложения», где «Приложения» уже активна.
 */
object AppsEntry {

    /** Действие для кнопок устройства и внешних вызовов; строка зафиксирована в манифесте. */
    const val ACTION_SHOW_APPS: String = "io.github.bookshelf.action.SHOW_APPS"

    /**
     * Режим по входящему интенту.
     *
     * @param action действие интента, `null` — запуск из кода.
     * @param hasLauncherCategory пришёл ли интент как запуск приложения (MAIN/LAUNCHER).
     */
    fun modeOf(action: String?, hasLauncherCategory: Boolean): AppsEntryMode =
        if (action == ACTION_SHOW_APPS || hasLauncherCategory) AppsEntryMode.STANDALONE else AppsEntryMode.TAB

    /** Режим по интенту активити: категория LAUNCHER проверяется без учёта прочих категорий. */
    fun modeOf(intent: Intent?): AppsEntryMode = modeOf(
        action = intent?.action,
        hasLauncherCategory = intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true,
    )

    /**
     * Нижняя панель есть в обоих режимах (решение Александра 02.11.2026).
     *
     * Сначала он выбрал её и для отдельного запуска тоже, посмотреть, как это работает; вариант
     * без панели остаётся запасным. Пока флаг константный, чтобы экран не обрастал ветками.
     */
    const val HAS_TAB_BAR: Boolean = true
}
