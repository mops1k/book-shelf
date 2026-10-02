package io.github.bookshelf.core.testing

/**
 * Уровни Android, на которых обязаны проходить тесты.
 *
 * [MIN] = API 24: минимальная поддерживаемая версия (Android 7). Тесты на ней — не «дополнительная
 * галочка», а защита от случайного использования API новее целевого (например, `java.nio.file`
 * без core library desugaring или `java.time` без него же).
 *
 * [TARGET_DEVICE] = API 30: типичная прошивка e-ink устройства с ограничениями SAF,
 * которые проект обязан учитывать («Архитектура» §4.1).
 */
object TestSdk {
    const val MIN = 24
    const val TARGET_DEVICE = 30
    const val LATEST = 35
}
