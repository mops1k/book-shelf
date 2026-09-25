package io.github.bookshelf.core.readerlink.launch

import android.net.Uri
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запуск книги во внешней читалке (уровень L1).
 *
 * Граница модуля намеренно узкая: на вход — уже выбранный профиль и URI, на выход —
 * `AppResult<Unit>`. Никаких исключений наружу: любая проблема описывается [ReaderLaunchError]
 * и конвертируется в [AppError] через [toAppError].
 */
interface ReaderLauncher {

    /** Открыть документ. Повторный вызов для той же книги — нормальный сценарий «продолжить чтение». */
    suspend fun launch(request: LaunchRequest): AppResult<Unit>
}

/**
 * Всё, что нужно для запуска.
 *
 * @param profile выбранная читалка.
 * @param documentUri URI книги: `content://` (SAF/FileProvider) или `file://`.
 * @param mimeType MIME-тип; для FB2 — [io.github.bookshelf.core.readerlink.mime.MimeTypes.FALLBACK].
 * @param extension расширение файла — используется для выбора activity и для копии.
 * @param allowTempCopy разрешена ли временная копия как фолбэк (выключено, если копировать нельзя).
 */
data class LaunchRequest(
    val profile: ReaderProfile,
    val documentUri: Uri,
    val mimeType: String,
    val extension: String,
    val allowTempCopy: Boolean,
)

/**
 * Ошибки запуска читалки (docs/reader-integration.md §7).
 *
 * Наследоваться от [AppError] нельзя: это `sealed`-иерархия, закрытая в модуле `:core:common`,
 * а Kotlin требует объявлять наследников sealed-типа в том же модуле. Поэтому здесь своя
 * иерархия, а совместимость обеспечивает [toAppError].
 */
sealed interface ReaderLaunchError {

    /** Человекочитаемое пояснение для UI; null — показываем общий текст по типу ошибки. */
    val message: String?

    /** Читалка удалена или её activity больше не найдена (`ActivityNotFoundException`). */
    data class ActivityNotFound(
        override val message: String? = null,
    ) : ReaderLaunchError

    /**
     * Читалка отказалась принимать URI (`SecurityException`).
     * Поведение: автоповтор с копией в `cacheDir/export` и `content://` через FileProvider.
     */
    data class PermissionDeniedByReader(
        override val message: String? = null,
    ) : ReaderLaunchError

    /** Читалка открылась, но файл не читается; предложение «Попробовать скопировать файл». */
    data class FileUnreadable(
        override val message: String? = null,
    ) : ReaderLaunchError

    /** Всё остальное — не должно происходить, но обязано быть обработано. */
    data class Unknown(
        override val message: String? = null,
        val cause: Throwable? = null,
    ) : ReaderLaunchError
}

/** Перевод ошибки запуска в доменную [AppError]. */
fun ReaderLaunchError.toAppError(): AppError = when (this) {
    is ReaderLaunchError.ActivityNotFound ->
        AppError.NotFound(what = "reader activity", message = message)

    is ReaderLaunchError.PermissionDeniedByReader ->
        AppError.PermissionDenied(message = message)

    is ReaderLaunchError.FileUnreadable ->
        AppError.Io(message = message)

    is ReaderLaunchError.Unknown ->
        AppError.Unknown(message = message, cause = cause)
}

/**
 * Заглушка запуска.
 *
 * В Sprint 5 реализация будет перебирать три способа URI, пока какой-нибудь не сработает:
 * 1. `content://` (SAF DocumentsProvider или FileProvider) + `FLAG_GRANT_READ_URI_PERMISSION`;
 * 2. `android.fbreader.action.VIEW` + `file://` реального пути (если путь известен);
 * 3. копия файла: в общий каталог (`full`-flavor) либо в `cacheDir/export` + FileProvider.
 *
 * При `ActivityNotFoundException` собирается понятное сообщение и предлагается выбор другой
 * читалки; при `SecurityException` — автоповтор с копией (docs/reader-integration.md §7).
 */
@Singleton
class DefaultReaderLauncher @Inject constructor() : ReaderLauncher {

    // STUB(Sprint 5): получить Context, собрать Intent по профилю и перебрать стратегии URI.

    override suspend fun launch(request: LaunchRequest): AppResult<Unit> = TODO(
        "STUB(Sprint 5): собрать Intent и автоматически выбрать рабочий способ URI",
    )
}
