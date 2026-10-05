package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Доступен ли источник прогресса прямо сейчас.
 *
 * @param sourceKey значение колонки `reading_progress.source`.
 * @param available удалось ли источнику подключиться: у файловых это значит, что его файл или база
 *   читаются (нет доступа ко всей памяти — `false`).
 */
data class ProgressSourceStatus(val sourceKey: String, val available: Boolean)

/**
 * Что показывает диагностика источников прогресса.
 *
 * @param sources по строке на каждый **включённый** источник: выключенные не читаются и в отчёте
 *   смысла не имеют.
 * @param lastSourceKey чей прогресс записан последним во всей библиотеке; `null` — записей нет.
 * @param lastBookTitle название той книги, `null` — книгу не нашли (удалили после записи).
 * @param lastUpdatedAt когда запись сделана (миллисекунды), `null` — записей нет.
 */
data class ProgressSourcesReport(
    val sources: List<ProgressSourceStatus>,
    val lastSourceKey: String?,
    val lastBookTitle: String?,
    val lastUpdatedAt: Long?,
)

/**
 * Диагностика источников прогресса чтения.
 *
 * Отвечает на вопрос «почему у книги нет процента», на который снаружи не ответить: переключатель
 * включён, а данных нет — либо читалка не установлена, либо её файл вне доступа, либо книгу в ней
 * ещё не открывали. Первое и второе различает [ProgressSourceStatus.available], третье видно по
 * последней записи.
 *
 * Источники подключаются по одному и сразу отключаются: держать открытыми чужие базы незачем, а
 * подключаться к читалке через её API диагностика не должна (это подняло бы чужой сервис).
 */
/**
 * Порт диагностики: экран настроек не должен знать, как именно опрашиваются источники.
 *
 * Отдельный интерфейс ещё и потому, что свои типы в тестах не мокаются (ADR-012): экран настроек
 * подставляет сюда маленький фейк, а не поднимает Room и настоящие источники.
 */
fun interface ProgressSourcesReporter {

    /** Опросить включённые источники и сказать, что записано последним. */
    suspend fun check(): AppResult<ProgressSourcesReport>
}

@Singleton
class CheckProgressSources @Inject constructor(
    private val sources: ReadingProgressSources,
    private val settings: SettingsRepository,
    private val progress: ReadingProgressDao,
    private val books: BookRepository,
) : ProgressSourcesReporter {

    override suspend fun check(): AppResult<ProgressSourcesReport> = appRunCatching {
        val enabled = settings.current().reader.enabledProgressSources()
        val statuses = sources.all()
            .filter { source -> source.id in enabled }
            .map { source ->
                // Ошибка подключения — это «недоступен», а не сбой диагностики: у чужого файла
                // может не быть доступа, и падать из-за этого незачем.
                val available = runCatching { source.connect() }.getOrDefault(false)
                runCatching { source.disconnect() }
                ProgressSourceStatus(sourceKey = source.id.storageKey, available = available)
            }
        val last = progress.getAll().maxByOrNull { it.updatedAt }
        ProgressSourcesReport(
            sources = statuses,
            lastSourceKey = last?.source,
            lastBookTitle = last?.let { record -> books.getById(record.bookId).getOrNull()?.title },
            lastUpdatedAt = last?.updatedAt,
        )
    }
}
