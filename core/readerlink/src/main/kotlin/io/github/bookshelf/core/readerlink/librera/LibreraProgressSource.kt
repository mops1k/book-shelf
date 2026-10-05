package io.github.bookshelf.core.readerlink.librera

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ProgressStorage
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import io.github.bookshelf.core.readerlink.progress.readerPathMatches
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Прогресс книги из файлов Librera.
 *
 * Librera держит прогресс в JSON на общей памяти:
 * `/sdcard/Librera/profile.Librera/device.<модель>/app-Progress.json` (доля `p` на имя файла) и
 * `app-Recent.json` (недавние книги с реальными путями). Проверено на файлах устройства 05.10.2026.
 *
 * Что здесь неочевидно:
 * * имя папки — `device.<модель устройства>`, поэтому каталог **ищется**, а не зашивается; папок
 *   может быть несколько, и тогда записи сводятся по времени `t` (свежая побеждает);
 * * ключ у Librera — **имя файла**: две книги с одинаковым именем Librera сама не различает. Поэтому
 *   запись подтверждается по `app-Recent.json`: если имя занято книгой с другим путём, процент не
 *   наш и лучше его не записывать;
 * * позиции в устойчивом виде нет: Librera помнит прокрутку (`x`, `y`) и фрагмент текста (`pt`).
 *   В снимок идёт только процент — подставлять фрагмент текста вместо адреса значило бы врать.
 *
 * Доступа ко всей памяти нет (flavor `play`) — `connect()` вернёт `false`, и прогресс не появится.
 */
@Singleton
class LibreraProgressSource @Inject constructor(
    private val files: ProgressStorage,
) : ReadingProgressSource {

    override val id: ProgressSourceId = ProgressSourceId.LIBRERA_JSON

    /** Librera наружу «что открыто» не отдаёт: её читают по конкретной книге. */
    override val knowsOpenBook: Boolean = false

    /** Записи прогресса, прочитанные при `connect()`; ключ — имя файла книги. */
    private var progress: Map<String, LibreraJson.Record> = emptyMap()

    /** Недавние книги: по ним проверяется, что имя файла принадлежит именно нам. */
    private var recent: List<LibreraJson.Recent> = emptyList()

    override suspend fun connect(): Boolean {
        val devices = files.children(PROFILE_DIRECTORY).filter { it.startsWith(DEVICE_PREFIX) }
        // Папки нет вовсе (Librera не установлена или нет доступа ко всей памяти) — источника нет.
        if (devices.isEmpty()) return false
        var records: Map<String, LibreraJson.Record> = emptyMap()
        val opened = mutableListOf<LibreraJson.Recent>()
        devices.forEach { device ->
            val directory = "$PROFILE_DIRECTORY/$device"
            files.read("$directory/$PROGRESS_FILE")
                ?.let { text -> records = merge(records, LibreraJson.progress(text)) }
            files.read("$directory/$RECENT_FILE")
                ?.let { text -> opened += LibreraJson.recent(text) }
        }
        progress = records
        recent = opened
        return true
    }

    override suspend fun disconnect() {
        progress = emptyMap()
        recent = emptyList()
    }

    override suspend fun openedBook(): ReaderBookAddress? = null

    override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
        val record = progress[book.fileName]
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения Librera",
                    message = "в файлах Librera нет записи для ${book.fileName}",
                ),
            )
        if (!isOursByRecent(book)) {
            return AppResult.failure(
                AppError.NotFound(
                    what = "прогресс чтения Librera",
                    message = "запись Librera для ${book.fileName} относится к другой книге с тем же именем",
                ),
            )
        }
        return AppResult.success(
            ReadingProgressSnapshot(
                percent = record.percent,
                pageIndex = null,
                locatorJson = null,
                // `t` в записи Librera — время последнего чтения; по нему сравниваются источники.
                updatedAt = record.time.takeIf { it > 0L },
            ),
        )
    }

    /**
     * Наша ли это запись.
     *
     * Список недавних короткий (в файле с устройства — 37 книг), и его молчание ничего не доказывает:
     * книга могла выпасть из списка, а запись прогресса остаться. Отказ только тогда, когда имя файла
     * в списке **есть**, но наш путь среди совпадений не встречается — тогда процент принадлежит
     * другой книге с тем же именем.
     */
    private fun isOursByRecent(book: ProgressBookRef): Boolean {
        val paths = recent.filter { it.fileName == book.fileName }.map { it.path }
        if (paths.isEmpty()) return true
        return paths.any { readerPathMatches(it, book) }
    }

    /** Свежая запись побеждает: папок `device.*` может быть несколько. */
    private fun merge(
        first: Map<String, LibreraJson.Record>,
        second: Map<String, LibreraJson.Record>,
    ): Map<String, LibreraJson.Record> = (first.keys + second.keys).associateWith { name ->
        val left = first[name]
        val right = second[name]
        when {
            left == null -> second.getValue(name)
            right == null -> left
            right.time >= left.time -> right
            else -> left
        }
    }

    private companion object {

        /** Каталог профиля Librera на общей памяти. */
        const val PROFILE_DIRECTORY = "/sdcard/Librera/profile.Librera"

        /** Папка конкретного устройства: `device.HiBreak`, `device.Bigme B6 Color` и т. п. */
        const val DEVICE_PREFIX = "device."

        const val PROGRESS_FILE = "app-Progress.json"
        const val RECENT_FILE = "app-Recent.json"
    }
}
