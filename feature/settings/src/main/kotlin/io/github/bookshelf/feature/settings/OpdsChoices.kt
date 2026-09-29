package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.opds.download.DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE

/**
 * Варианты возраста кэша фидов, минуты: сколько открытый каталог берётся из памяти без сети.
 *
 * Один список на экран и на проверку в [SettingsViewModel]: если бы экран рисовал свои значения,
 * а ViewModel проверяла другие, «лишний» вариант молча не сохранялся бы — пользователь нажимал бы
 * чип, и ничего не происходило.
 */
internal val FEED_CACHE_CHOICES_MINUTES: List<Int> = listOf(5, 15, 60)

/**
 * Человеческие подписи шаблонов пути скачанного файла.
 *
 * Ключи обязаны совпадать с
 * [io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES] — это проверяет тест:
 * подпись без самого шаблона означала бы чип, который ничего не сохраняет, а шаблон без подписи —
 * вариант, который пользователь не видит.
 */
internal val DOWNLOAD_FILE_NAME_TEMPLATE_LABELS: Map<String, String> = mapOf(
    DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE to "Автор — название",
    "{author}/{title}.{ext}" to "Автор / название",
    "{author}/{series}/{title}.{ext}" to "Автор / серия / название",
    "{title}.{ext}" to "Только название",
)
