package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.data.sync.ProgressSourcesReport
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.readerlink.progress.ProgressSourceKeys

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
        if (settings.isXReaderProgressEnabled) add(R.string.settings_readers_progress_source_xreader)
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

/**
 * Подпись строки «Читалки по умолчанию» на первом уровне: сколько читалок нашлось.
 *
 * Строка ведёт на отдельный экран со списком (требование Александра 05.10.2026), поэтому должна
 * отвечать, есть ли там вообще что-то смотреть: пустой экран человек воспринимает как поломку.
 */
internal fun readersFoundText(found: Int): AppText = if (found == 0) {
    AppText(R.string.settings_readers_defaults_none)
} else {
    AppText(R.string.settings_readers_defaults_found, found)
}

/**
 * Человеческое имя источника прогресса по значению колонки `reading_progress.source`.
 *
 * `null` — источник незнаком (запись оставила версия приложения, где такого источника ещё не было):
 * показываем «источник незнаком», а не пустую строку и не падение.
 */
internal fun progressSourceNameRes(sourceKey: String): Int? = when (sourceKey) {
    ProgressSourceKeys.FBREADER_AIDL -> R.string.settings_readers_progress_source_fbreader
    ProgressSourceKeys.KOREADER_SIDECAR -> R.string.settings_readers_progress_source_koreader
    ProgressSourceKeys.COOLREADER_DB -> R.string.settings_readers_progress_source_coolreader
    ProgressSourceKeys.POCKETBOOK_DB -> R.string.settings_readers_progress_source_pocketbook
    ProgressSourceKeys.LIBRERA_JSON -> R.string.settings_readers_progress_source_librera
    ProgressSourceKeys.XREADER_CACHE -> R.string.settings_readers_progress_source_xreader
    else -> null
}

/**
 * Имена всех источников прогресса для строки диагностики: ресурс имени и читается ли источник.
 *
 * Именно **все**, а не только проблемные (просьба Александра 05.10.2026): строка отвечает на вопрос
 * «с кем приложение может работать», и без списка читаемых непонятно, есть ли они вообще. Порядок —
 * порядок опроса источников, он же порядок переключателей на подэкране прогресса.
 *
 * Незнакомый ключ (запись оставила версия приложения с другим набором источников) не выпадает из
 * списка: он показывается как «источник незнаком», иначе строка обещала бы больше, чем есть.
 */
internal fun progressSourcesNames(report: ProgressSourcesReport): List<Pair<Int, Boolean>> =
    report.sources.map { status ->
        val nameRes = progressSourceNameRes(status.sourceKey)
            ?: R.string.settings_about_progress_source_unknown
        nameRes to status.available
    }
