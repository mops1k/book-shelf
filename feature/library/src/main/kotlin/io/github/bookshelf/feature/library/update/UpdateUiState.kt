package io.github.bookshelf.feature.library.update

import io.github.bookshelf.core.update.download.UpdateDownloadState

/**
 * Состояние диалога обновления на полке (ADR-018).
 *
 * Отдельно «проверяю», «есть версия» и «ручной результат»: автоматическая проверка либо молчит,
 * либо показывает диалог, а ручная — ещё и отвечает текстом, потому что пользователь ждёт ответа.
 */
data class UpdateUiState(
    /** Диалог виден: есть версия новее. */
    val visible: Boolean = false,
    /** Идёт проверка (ручная — с индикатором). */
    val checking: Boolean = false,
    /** Версия, которую предлагаем, например «2.0.2». */
    val versionName: String = "",
    /** Что нового: тело релиза как есть. */
    val notes: String = "",
    /** Страница релиза — запасной путь и то, что открывается, пока скачивание не сделано. */
    val pageUrl: String = "",
    /** Ответ на ручную проверку: «Обновлений нет» или «Не удалось проверить». */
    val manualResult: ManualCheckResult? = null,
    /** Скачивание APK обновления (фаза 3 ADR-018). */
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
    /** Нужно разрешение «установка неизвестных приложений». */
    val permissionRequired: Boolean = false,
    /** APK, который предлагается скачать (из ассетов релиза). */
    val assetUrl: String = "",
    val assetName: String = "",
)

/** Ответ на ручную проверку из «О приложении» (ADR-018). */
enum class ManualCheckResult { NO_UPDATE, FAILED, UNAVAILABLE }
