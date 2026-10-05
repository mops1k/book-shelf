package io.github.bookshelf.core.readerlink.progress

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реестр источников прогресса: кого спрашивать прямо сейчас.
 *
 * Зачем реестр, а не набор зависимостей по одной:
 * * источников уже несколько, и добавление каждого не должно менять сценарий синхронизации;
 * * во время чтения спрашивать разрешено **только** открытую сейчас читалку — иначе мы читаем
 *   чужие файлы на карте, пока человек читает, а на слабом e-ink именно эта нагрузка уже роняла
 *   читалку (ANR 30.09.2026);
 * * порядок обхода должен быть детерминированным — он задаётся [ProgressSourceId], а не порядком
 *   биндингов в DI, который меняется от сборки к сборке.
 *
 * Источники, которых нет в реестре (читалка не реализована или выключена сборкой), не спрашиваются
 * никогда: открытая читалка без источника даёт пустой список, а не «опросим всех на всякий случай».
 */
@Singleton
class ReadingProgressSources @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards ReadingProgressSource>,
) {

    /** Все известные источники в порядке [ProgressSourceId]. */
    fun all(): List<ReadingProgressSource> = sources.sortedBy { it.id.ordinal }

    /** Источник по каналу; `null`, если такого источника в сборке нет. */
    fun byId(id: ProgressSourceId): ReadingProgressSource? = sources.firstOrNull { it.id == id }

    /**
     * Источники для прохода.
     *
     * @param activeSourceId читалка, открытая сейчас; `null` — чтения нет и спрашивать можно всех.
     */
    fun forPass(activeSourceId: ProgressSourceId?): List<ReadingProgressSource> =
        if (activeSourceId == null) all() else listOfNotNull(byId(activeSourceId))
}
