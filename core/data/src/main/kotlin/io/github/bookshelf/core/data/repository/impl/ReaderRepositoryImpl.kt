package io.github.bookshelf.core.data.repository.impl

import android.net.Uri
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.FormatDefaultDao
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.database.dao.ReaderProfileDao
import io.github.bookshelf.core.database.entity.FormatDefaultEntity
import io.github.bookshelf.core.database.entity.ReaderProfileEntity
import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.discovery.InstalledReader
import io.github.bookshelf.core.readerlink.launch.LaunchRequest
import io.github.bookshelf.core.readerlink.launch.ReaderExport
import io.github.bookshelf.core.readerlink.launch.ReaderLauncher
import io.github.bookshelf.core.readerlink.mime.MimeTypes
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [ReaderRepository].
 *
 * Что должна делать (Sprint 5, ADR-005):
 * * `installedReaders` — профили из `reader_profiles`, объединённые с найденными в системе
 *   (`PackageManager.queryIntentActivities` в `:core:readerlink`);
 * * `defaultForFormat` / `setDefault` — чтение и запись `format_defaults`;
 * * `openBook` — резолвер (override книги → дефолт формата → последняя → диалог) и запуск через
 *   `:core:readerlink`; `ActivityNotFoundException` превращается в понятный `AppError`, а не падение.
 */
@Singleton
class ReaderRepositoryImpl @Inject constructor(
    private val discovery: ReaderDiscovery,
    private val launcher: ReaderLauncher,
    private val export: ReaderExport,
    private val profileDao: ReaderProfileDao,
    private val bookDao: BookDao,
    private val formatDefaultDao: FormatDefaultDao,
    private val session: ReaderSession,
    private val clock: AppClock,
) : ReaderRepository {

    /**
     * Профили берутся из БД, а установленные читалки подкладываются сверху.
     *
     * Так пользователь сохраняет свои настройки (приоритет, привязку к формату, включённость),
     * а читалка, установленная позже, появляется сама. Обратный порядок стёр бы настройки при
     * каждом перезапуске процесса.
     */
    override fun installedReaders(): Flow<List<ReaderProfileSummary>> = combine(
        profileDao.observeAll(),
        flow { emit(discovery.installedReaders()) },
    ) { stored: List<ReaderProfileEntity>, installed: List<InstalledReader> ->
        val byPackage = installed.associate { it.profile.packageName to it }
        (stored.map { it.toSummary() } + installed.filter { found ->
            stored.none { it.packageName == found.profile.packageName }
        }.map { found ->
            ReaderProfileSummary(
                id = found.profile.id,
                name = found.profile.name,
                packageName = found.profile.packageName,
                enabled = true,
                isBuiltIn = found.profile.isBuiltIn,
                priority = 0,
            )
        }).sortedWith(
            compareByDescending<ReaderProfileSummary> { it.priority }.thenBy { it.name },
        )
            .also { byPackage }
    }

    override fun defaultForFormat(format: String): Flow<String?> =
        formatDefaultDao.observeAll().map { entries -> entries.firstOrNull { it.format == format }?.profileId }

    /** Привязка формата к читалке пишется одним upsert; `null` снимает привязку строкой. */
    override suspend fun setDefault(format: String, profileId: String?): AppResult<Unit> =
        appRunCatching {
            if (profileId == null) {
                formatDefaultDao.deleteByFormat(format)
            } else {
                formatDefaultDao.upsert(
                    FormatDefaultEntity(format = format, profileId = profileId),
                )
            }
        }

    /**
     * Запуск книги.
     *
     * Профиль выбирается по правилу «явный выбор → привязка к формату → любая включённая».
     * Порядок важен: молча открыть книру в другой читалке, чем выбрал пользователь, — хуже,
     * чем показать сообщение с выбором.
     */
    override suspend fun openBook(bookId: String, profileId: String?): AppResult<Unit> {
        val book = bookDao.getById(bookId)
            ?: return AppResult.failure(AppError.NotFound(what = "book", message = "Книга $bookId не найдена"))
        val profile = resolveProfile(profileId, book.extension)
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "reader",
                    message = "Читалка для «${book.extension}» не выбрана. " +
                        "Укажите её в настройках → «Читалки».",
                ),
            )
        val request = LaunchRequest(
            profile = profile,
            documentUri = Uri.parse(book.documentUri),
            mimeType = MimeTypes.mimeOf(book.extension),
            extension = book.extension,
        )
        // Читалка, которая не умеет открывать по ссылке, получает копию сразу: пробовать
        // `content://` и ждать отказа незачем.
        if (!profile.supportsContentUri) {
            return launchExportedCopy(book.id, book.documentUri, request)
        }
        val direct = launcher.launch(request)
        // Отказ читалки — не тупик: есть один запасной ход, временная копия файла.
        if (direct.isSuccess) {
            markOpened(book.id)
            return direct
        }
        return launchExportedCopy(book.id, book.documentUri, request)
    }

    /**
     * Запасной запуск: копия книги и повторная попытка.
     *
     * Разрешение на копию проверяет сам экспорт (`reader.tempExportEnabled`): это запись на диск,
     * и отвечать за неё должен тот, кто пишет. Если экспорт выключен, честнее сказать человеку,
     * что включить, чем молча повторить запуск с адресом, который читалка уже отвергла.
     *
     * Ошибка копирования возвращается как есть: «файл больше лимита» — это то, что человек может
     * исправить (поднять лимит в «Библиотеке и сканировании»), а общее «не удалось открыть» — нет.
     */
    private suspend fun launchExportedCopy(
        bookId: String,
        documentUri: String,
        request: LaunchRequest,
    ): AppResult<Unit> {
        val exported = export.exportedReaderUri(documentUri)
        val exportedUri = exported.getOrNull()
            ?: return AppResult.failure(
                exported.errorOrNull() ?: AppError.Unknown(message = "Копия книги не подготовлена"),
            )
        val launched = launcher.launch(request.copy(documentUri = exportedUri))
        // Отмечаем только по факту запуска: читалка, которая не открылась, чтением не стала,
        // и запрещать фоновую работу после отказа было бы вредно.
        if (launched.isSuccess) markOpened(bookId)
        return launched
    }

    /**
     * Книгу открыли: она читается прямо сейчас.
     *
     * Требование Александра (30.09.2026): статус меняется при открытии из полки **независимо** от
     * интеграции с читалкой. Раньше «читаю» появлялось только из синхронизации прогресса, а она
     * работает лишь когда читалка отдала позицию и книгу удалось опознать, — без этого книга
     * оставалась «не прочитано», хотя её открыли.
     *
     * Уже выставленный статус не переписываем: «прочитано» синхронизация и открытие сбрасывать не
     * должны — человек мог открыть законченную книгу, чтобы перечитать главу. Дата открытия при
     * этом обновляется всегда: по ней полка сортирует «Дата открытия» и собирает полку «Читаю».
     *
     * Отдельного запроса в DAO нет намеренно: строку читаем существующим `getById` и пишем
     * существующим `update`, а не заводим третий метод на два поля (порог detekt по числу функций
     * в `BookDao` уже на пределе).
     */
    private suspend fun markOpened(bookId: String) {
        val current = bookDao.getById(bookId) ?: return
        bookDao.update(
            current.copy(
                lastOpenedAt = clock.nowMillis(),
                readingStatus = if (current.readingStatus == ReadingStatus.UNREAD) {
                    ReadingStatus.READING
                } else {
                    current.readingStatus
                },
            ),
        )
        // Сессия чтения — та же причина: пока читалка открыта, тяжёлые работы уступают ей дорогу.
        session.markOpened(bookId)
    }

    /**
     * Порядок выбора: явный id → привязка формата → FBReader → сохранённые включённые.
     *
     * **Запасного шага «любая найденная читалка» здесь нет намеренно.** Под книжные MIME-типы
     * подходит не только читалка: на устройстве систему уводило в AniLabX — приложение для
     * аниме. Молча открыть книгу не в той программе хуже, чем сказать «читалка не выбрана»,
     * поэтому дальше путь один: `null` → сообщение и раздел «Читалки» в настройках.
     *
     * Внутри шагов порядок задаёт [FbReaderPreset.rankOf].
     */
    private suspend fun resolveProfile(profileId: String?, extension: String): ReaderProfile? {
        if (profileId != null) {
            profileDao.getById(profileId)?.let { return it.toProfile() }
        }
        formatDefaultDao.getByFormat(extension)?.profileId?.let { defaultId ->
            profileDao.getById(defaultId)?.let { return it.toProfile() }
        }
        val installed = discovery.installedReaders()
        val byPackage = installed.associate { it.profile.packageName to it.profile }
        // Сохранённые пользователем профили — его явный выбор, им верят.
        val stored = profileDao.observeEnabled().first()
            .mapNotNull { byPackage[it.packageName] }
        if (stored.isNotEmpty()) {
            return stored.minByOrNull { FbReaderPreset.rankOf(it.packageName) }
        }
        // Ничего не выбрано: берём только проверенную читалку (ADR-005). Все прочие найденные
        // пакеты игнорируются намеренно — иначе книга ушла бы в приложение, которое к книгам
        // отношения не имеет.
        return installed
            .map { it.profile }
            .minByOrNull { FbReaderPreset.rankOf(it.packageName) }
    }
}

/** Профиль из БД в доменную модель; MIME хранятся строкой и делятся разделителем. */
private fun ReaderProfileEntity.toProfile(): ReaderProfile = ReaderProfile(
    id = id,
    name = name,
    packageName = packageName,
    activityName = activityName,
    mimeTypes = mimeTypes.split(MIME_SEPARATOR).filter { it.isNotBlank() },
    supportsContentUri = supportsContentUri,
    extrasJson = extrasJson,
    isBuiltIn = isBuiltIn,
    enabled = enabled,
    priority = priority,
)

private fun ReaderProfileEntity.toSummary(): ReaderProfileSummary = ReaderProfileSummary(
    id = id,
    name = name,
    packageName = packageName,
    enabled = enabled,
    isBuiltIn = isBuiltIn,
    priority = priority,
)

/** Разделитель списка MIME в строковом поле: запятая безопасна, так как MIME её не содержит. */
private const val MIME_SEPARATOR = ","
