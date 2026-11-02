package io.github.bookshelf.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * Состояние лаунчера: предпочтения пользователя в списке приложений (ADR-015).
 *
 * Хранится отдельным JSON-файлом, а не ключами настроек: набор избранных пакетов, скрытых и
 * счётчиков запусков растёт и меняется чаще, чем схема настроек, и городить под каждый ключ
 * отдельную запись DataStore незачем.
 *
 * Все поля имеют умолчания — файл, которого нет или который от более новой версии, читается как
 * «ничего не настроено», а не как ошибка.
 */
@Serializable
data class LauncherState(
    /** Пакеты, закреплённые вверху списка. */
    val favoritePackages: Set<String> = emptySet(),
    /** Пакеты, которые пользователь убрал из списка долгим нажатием. */
    val hiddenPackages: Set<String> = emptySet(),
    val sortMode: AppSortMode = AppSortMode.NAME,
    /** Системные приложения скрыты, пока пользователь не включит их галочкой. */
    val showSystemApps: Boolean = false,
    /** Пакет → сколько раз запускали из нашего списка. */
    val launchCounts: Map<String, Int> = emptyMap(),
    /**
     * Пункт «Приложения» виден системе: от него зависит `activity-alias` для аппаратной кнопки
     * устройства. Выключен по умолчанию — иначе приложение без спроса появляется в чужом списке.
     */
    val appsItemVisible: Boolean = false,
    /**
     * BACK в корне в роли HOME сворачивает приложение вместо выхода (решение 02.11.2026).
     * Действует только когда BookShelf выбран домашним экраном.
     */
    val collapseInsteadOfExit: Boolean = true,
) {

    /**
     * Учесть запуск приложения из списка.
     *
     * Счётчик — единственный источник сортировки «по частоте». Считаем только запуски из нашего
     * списка: читать историю запусков системы мы не можем и не должны (это чужие данные).
     */
    fun withLaunch(packageName: String): LauncherState = copy(
        launchCounts = launchCounts + (packageName to (launchCounts[packageName] ?: 0) + 1),
    )
}

/** Порядок списка приложений (ADR-015). */
@Serializable
enum class AppSortMode {
    /** По подписи, русским алфавитом. */
    NAME,

    /** По числу запусков из нашего списка, при равенстве — по подписи. */
    LAUNCH_FREQUENCY,
}
