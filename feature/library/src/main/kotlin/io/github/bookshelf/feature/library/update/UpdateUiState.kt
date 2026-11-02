package io.github.bookshelf.feature.library.update

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
)

/** Ответ на ручную проверку из «О приложении» (ADR-018). */
enum class ManualCheckResult { NO_UPDATE, FAILED, UNAVAILABLE }
