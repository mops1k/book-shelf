package io.github.bookshelf.core.update.checker

import io.github.bookshelf.core.update.model.ReleaseAsset
import io.github.bookshelf.core.update.model.ReleaseInfo

/**
 * Результат проверки обновлений (ADR-018).
 *
 * Отдельные состояния для «выключено», «недавно проверяли» и «ошибка сети»: пользователю они
 * говорят разное, и смешивать их в одно «нет обновлений» значит врать в интерфейсе.
 */
sealed interface UpdateCheck {

    /** Проверка выключена настройкой или сборка из Google Play: сети не касаемся. */
    data object Disabled : UpdateCheck

    /** Недавно проверяли — до следующей автоматической проверки ждём сутки. */
    data object Throttled : UpdateCheck

    /** Проверка прошла, новее нет. */
    data object None : UpdateCheck

    /** Есть новая версия; [asset] — APK для этого flavor (`null`, если ассета нет). */
    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset?) : UpdateCheck

    /**
     * Проверка не удалась: нет сети, GitHub недоступен, ответ не разобран.
     *
     * @param reason короткая причина для интерфейса и лога.
     */
    data class Failed(val reason: String) : UpdateCheck
}
