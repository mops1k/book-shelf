package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.designsystem.text.AppText

/**
 * Текст для человека в настройках — общий тип приложения.
 *
 * Псевдоним, а не свой тип: правило «текст рождается вне Composable, показывается на экране» одно
 * на всё приложение, и второй такой же класс только разошёлся бы с первым.
 */
internal typealias SettingsText = AppText

/**
 * Подпись строки «Прогресс чтения» на первом уровне экрана «Читалки».
 *
 * Блок переключателей переехал на отдельный подэкран (требование Александра 05.10.2026: «а то экран
 * сильно разрастается»), и на первом уровне осталась одна строка. Подпись отвечает на вопрос
 * «включено ли что-нибудь и с кем именно»: без неё строка выглядит как настройка, которую забыли
 * заполнить.
 *
 * Порядок имён — порядок опроса источников (`ProgressSourceId`): подпись не должна меняться от того,
 * в каком порядке включали читалки. Когда включено несколько, называется первая и счёт остальных:
 * перечислять пять имён в подписи некуда.
 */
internal fun progressSourcesSummary(settings: AppSettingsUi): AppText {
    val sources = buildList {
        if (settings.isFbReaderProgressEnabled) add(R.string.settings_readers_progress_source_fbreader)
        if (settings.isKoReaderProgressEnabled) add(R.string.settings_readers_progress_source_koreader)
        if (settings.isCoolReaderProgressEnabled) add(R.string.settings_readers_progress_source_coolreader)
        if (settings.isPocketBookProgressEnabled) add(R.string.settings_readers_progress_source_pocketbook)
        if (settings.isLibreraProgressEnabled) add(R.string.settings_readers_progress_source_librera)
    }
    return when (sources.size) {
        0 -> AppText(R.string.settings_readers_progress_none)
        1 -> AppText(R.string.settings_readers_progress_one, AppText(sources.first()))
        else -> AppText(
            R.string.settings_readers_progress_many,
            AppText(sources.first()),
            sources.size - 1,
        )
    }
}
