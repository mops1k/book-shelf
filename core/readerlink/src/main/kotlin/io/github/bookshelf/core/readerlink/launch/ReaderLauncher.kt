package io.github.bookshelf.core.readerlink.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import io.github.bookshelf.core.readerlink.profile.ReaderProfile

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
 */
data class LaunchRequest(
    val profile: ReaderProfile,
    val documentUri: Uri,
    val mimeType: String,
    val extension: String,
)

/**
 * Ошибки запуска читалки («Читалки» §7).
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
 * читалки; при `SecurityException` — автоповтор с копией («Читалки» §7).
 */
@Singleton
class DefaultReaderLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderLauncher {

    /**
     * Запуск книги в выбранной читалке (L1, «Читалки» §7).
     *
     * **Каждая попытка ограничена пакетом профиля.** Общего `ACTION_VIEW` без пакета здесь
     * нет намеренно: под книжные MIME-типы подходит не только читалка (на устройстве подходили
     * AniLabX и ещё несколько посторонних приложений), и система без нашего участия отдавала
     * книгу первому попавшемуся. Пользователь выбрал читалку — открываем в ней, а если она не
     * справилась, сообщаем об этом, вместо того чтобы молча подменить приложение.
     *
     * Внутри пакета перебираются два способа, потому что один не срабатывает всегда:
     * 1. явный компонент «пакет + activity», если профиль её знает;
     * 2. `ACTION_VIEW` с MIME внутри пакета — пакет сам выберет подходящую activity.
     *
     * Второй способ нужен потому, что activity в профиле может быть переименована: жёстко
     * заколоченный компонент тогда даёт «не найдено», хотя читалка стоит и формат умеет.
     *
     * `ActivityNotFoundException` — нормальная ситуация, а не сбой: читалка может быть
     * установлена, но не уметь этот формат.
     */
    override suspend fun launch(request: LaunchRequest): AppResult<Unit> = runCatching {
        val profile = request.profile
        check(profile.packageName.isNotBlank()) {
            "У профиля читалки не задан пакет"
        }
        val attempts = buildList {
            profile.activityName?.let { activityName ->
                add(viewIntent(request.documentUri, profile.packageName, activityName))
            }
            add(viewIntent(request.documentUri, profile.packageName, activityName = null))
        }

        var lastError: Throwable? = null
        for (intent in attempts) {
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved == null) {
                lastError = ActivityNotFoundException("Нет приложения для ${request.mimeType}")
                continue
            }
            try {
                context.startActivity(intent)
                return@runCatching
            } catch (notFound: ActivityNotFoundException) {
                lastError = notFound
            }
        }
        throw lastError ?: ActivityNotFoundException("Нет приложения для ${request.mimeType}")
    }.fold(
        onSuccess = { AppResult.success(Unit) },
        onFailure = { error ->
            AppResult.failure(
                if (error is ActivityNotFoundException) {
                    AppError.NotFound(
                        what = "reader",
                        message = "Читалка «${request.profile.name}» не открывает этот формат",
                    )
                } else if (error is SecurityException) {
                    AppError.PermissionDenied(message = "Читалка отказала в доступе к файлу")
                } else {
                    AppError.Unknown(message = error.message, cause = error)
                },
            )
        },
    )

    /**
     * Один вариант intent на запуск, всегда привязанный к пакету читалки.
     *
     * **Адрес без MIME-типа.** Проверено на устройстве (adb, запущенный FBReader Premium 3.8.31,
     * 06.10.2026): `ACTION_VIEW` + адрес **с типом** книгу не открывает, а **без типа** — открывает.
     * FBReader объявляет `VIEW` на любой тип, и явный тип ломает его разбор адреса: читалка
     * запускается и остаётся на прежней книге (дефект Александра: «открываю предыстория книга 1,
     * открывается в фбридере вселенная неудачников»).
     *
     * **`CLEAR_TOP`.** Без него уже открытая книга остаётся на экране: читалка поднимает свою
     * activity, но новую книгу не показывает. `CLEAR_TOP` заставляет её пересоздать activity с нашим
     * адресом.
     */
    internal fun viewIntent(
        documentUri: Uri,
        packageName: String,
        activityName: String?,
    ): Intent = Intent(Intent.ACTION_VIEW).apply {
        setData(documentUri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addCategory(Intent.CATEGORY_DEFAULT)
        if (activityName != null) setClassName(packageName, activityName)
        setPackage(packageName)
    }
}
