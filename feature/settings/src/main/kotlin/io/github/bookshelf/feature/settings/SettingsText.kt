package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.data.sync.ProgressSourcesReport
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.readerPackagesOf
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
internal fun progressSourcesSummary(
    settings: AppSettingsUi,
    installedPackages: Set<String>,
): AppText {
    // Удалённую читалку не называем: её в приложении быть не должно (требование Александра,
    // 06.10.2026 — «если читалка удалена с устройства, её в приложении нигде не должно быть видно»).
    val sources = progressSourceLines(settings)
        .filter { line -> line.enabled && line.packages.any { packageName -> packageName in installedPackages } }
        .map { line -> line.titleRes }
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

/** Строка сводки: включён ли источник, какие пакеты читалок за него отвечают и как он называется. */
private data class ProgressSourceLine(
    val enabled: Boolean,
    val packages: Set<String>,
    val titleRes: Int,
)

/** Источники в порядке опроса — он же порядок переключателей на подэкране «Прогресс чтения». */
private fun progressSourceLines(settings: AppSettingsUi) = listOf(
    ProgressSourceLine(
        settings.isFbReaderProgressEnabled,
        setOf(FBREADER_PACKAGE),
        R.string.settings_readers_progress_source_fbreader,
    ),
    ProgressSourceLine(
        settings.isKoReaderProgressEnabled,
        setOf(KOREADER_PACKAGE),
        R.string.settings_readers_progress_source_koreader,
    ),
    ProgressSourceLine(
        settings.isCoolReaderProgressEnabled,
        setOf(COOLREADER_PACKAGE),
        R.string.settings_readers_progress_source_coolreader,
    ),
    ProgressSourceLine(
        settings.isPocketBookProgressEnabled,
        setOf(POCKETBOOK_PACKAGE),
        R.string.settings_readers_progress_source_pocketbook,
    ),
    ProgressSourceLine(
        settings.isLibreraProgressEnabled,
        LIBRERA_PACKAGES,
        R.string.settings_readers_progress_source_librera,
    ),
    ProgressSourceLine(
        settings.isXReaderProgressEnabled,
        setOf(XREADER_PACKAGE),
        R.string.settings_readers_progress_source_xreader,
    ),
)

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
 * Строка диагностики источников: заголовок и имена источников в ней.
 *
 * @param titleRes «Читаются» или «Не читается».
 * @param names ресурсы имён в порядке опроса источников.
 */
internal data class ProgressSourcesLine(val titleRes: Int, val names: List<Int>)

/**
 * Разложить источники по строкам диагностики.
 *
 * Читаемые и нечитаемые — **разными строками** (просьба Александра 05.10.2026): в одну строку список
 * из шести имён не помещался, и его приходилось обрезать. Строка «Не читается» появляется, только
 * когда есть проблемные; порядок имён — порядок опроса источников, тот же, что у переключателей на
 * подэкране прогресса.
 *
 * Незнакомый ключ (запись оставила версия приложения с другим набором источников) не выпадает из
 * списка: он показывается как «источник незнаком», иначе диагностика обещала бы больше, чем есть.
 */
internal fun progressSourcesLines(report: ProgressSourcesReport): List<ProgressSourcesLine> {
    val named = report.sources.map { status ->
        val nameRes = progressSourceNameRes(status.sourceKey)
            ?: R.string.settings_about_progress_source_unknown
        nameRes to status.available
    }
    return buildList {
        val read = named.filter { (_, available) -> available }.map { (nameRes, _) -> nameRes }
        if (read.isNotEmpty()) add(ProgressSourcesLine(R.string.settings_about_progress_sources_read, read))
        val failed = named.filter { (_, available) -> !available }.map { (nameRes, _) -> nameRes }
        if (failed.isNotEmpty()) {
            add(ProgressSourcesLine(R.string.settings_about_progress_sources_failed, failed))
        }
    }
}

/** Пакеты установленных читалок: по ним видно, что показывать, а что скрыть. */
internal fun installedReaderPackages(state: SettingsUiState): Set<String> =
    state.installedReaders.mapTo(mutableSetOf()) { reader -> reader.packageName }

/**
 * Источники прогресса, которые показываем: только те, чья читалка установлена.
 *
 * Требование Александра (06.10.2026): удалённой читалки в приложении не должно быть видно нигде —
 * в том числе в переключателях «Прогресс из …». Порядок источников — порядок объявления
 * [ProgressSourceId], он же порядок переключателей на подэкране.
 */
internal fun visibleProgressSources(state: SettingsUiState): List<ProgressSourceId> {
    val installed = installedReaderPackages(state)
    return ProgressSourceId.entries.filter { source ->
        readerPackagesOf(source).any { packageName -> packageName in installed }
    }
}

/** Пакеты читалок для проверки «установлена ли»: те же строки, что и в [readerPackagesOf]. */
private const val FBREADER_PACKAGE = "com.fbreader"
private const val KOREADER_PACKAGE = "org.koreader.launcher"
private const val COOLREADER_PACKAGE = "org.coolreader"
private const val POCKETBOOK_PACKAGE = "com.obreey.reader"
private const val XREADER_PACKAGE = "com.xrz.xreaderV3"
private val LIBRERA_PACKAGES = setOf("com.foobnix.pdf.reader", "com.foobnix.pro.pdf.reader")
