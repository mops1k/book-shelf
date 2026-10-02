package io.github.bookshelf.core.readerlink.resolve

import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.core.readerlink.profile.ReaderProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбор читалки для конкретной книги.
 *
 * Порядок разрешения строго фиксирован («Архитектура» §7, «Читалки» §6):
 * 1. явный выбор пользователя для этой книги (`book_reader_override`);
 * 2. читалка по умолчанию для формата (`format_defaults`);
 * 3. последняя успешно использованная для этого формата;
 * 4. если подходящая ровно одна — она;
 * 5. иначе — наш диалог «Открыть в…» (не системный chooser: он плохо читается на e-ink).
 *
 * Перед выбором кандидаты фильтруются: профиль включён, пакет установлен, MIME/расширение
 * поддерживается, `queryIntentActivities` не пуст. Отсутствие читалки — не ошибка, а
 * [ReaderResolution.NoReaderAvailable].
 */
interface ReaderResolver {

    /**
     * @param bookId идентификатор книги для поиска override.
     * @param format расширение файла (`fb2`, `epub`, …) в нижнем регистре.
     * @param overrideProfileId явный выбор для книги; null — идём по общему порядку.
     */
    suspend fun resolve(
        bookId: String,
        format: String,
        overrideProfileId: String?,
    ): ReaderResolution
}

/** Результат разрешения читалки. */
sealed interface ReaderResolution {

    /** Читалка выбрана; [strategy] — с какого способа передачи URI начинать. */
    data class Resolved(
        val profile: ReaderProfile,
        val strategy: LaunchStrategy,
    ) : ReaderResolution

    /** Подходящих читалок несколько — показываем наш диалог «Открыть в…». */
    data class ChooseManually(
        val candidates: List<ReaderProfile>,
    ) : ReaderResolution

    /** Ни одной установленной читалки; UI предлагает установить или выбрать другую. */
    data object NoReaderAvailable : ReaderResolution

    /** Формат не поддерживается ни одним профилем (например, DJVU/CB7, «Форматы»). */
    data class NotSupported(
        val format: String,
    ) : ReaderResolution
}

/**
 * Способ передачи файла читалке, по приоритету («Читалки» §2.2).
 *
 * Рабочий способ фиксируется по итогам Sprint 0 и сохраняется в профиле.
 */
enum class LaunchStrategy {
    /** `content://` (FileProvider/SAF) + `FLAG_GRANT_READ_URI_PERMISSION`. */
    CONTENT_URI_WITH_GRANT,

    /** `android.fbreader.action.VIEW` + `file://` реального пути — исторически «родной» способ. */
    FILE_URI,

    /** `content://` FileProvider для копии в `cacheDir/export`. */
    FILE_PROVIDER,

    /** Временная копия файла в общий каталог или `cacheDir` с последующим `file://`. */
    TEMP_COPY,
}

/** Заглушка резолвера. */
@Singleton
class DefaultReaderResolver @Inject constructor(
    // Sprint 5: понадобится для порядка выбора читалки.
    @Suppress("UnusedPrivateProperty")
    profiles: ReaderProfileRepository,
    // Sprint 5: понадобится для порядка выбора читалки.
    @Suppress("UnusedPrivateProperty")
    discovery: ReaderDiscovery,
) : ReaderResolver {

    // STUB(Sprint 5): собрать кандидатов (profiles + discovery), отфильтровать по формату
    // и пройти порядок «override → дефолт формата → последняя → единственная → диалог».

    override suspend fun resolve(
        bookId: String,
        format: String,
        overrideProfileId: String?,
    ): ReaderResolution = TODO("STUB(Sprint 5): реализовать порядок выбора читалки")
}
