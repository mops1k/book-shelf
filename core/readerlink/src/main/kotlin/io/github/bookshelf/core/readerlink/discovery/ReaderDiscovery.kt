package io.github.bookshelf.core.readerlink.discovery

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.readerlink.mime.MimeTypes
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Поиск установленных читалок.
 *
 * Реализация обязана работать через `PackageManager.queryIntentActivities` и `<queries>` в
 * манифесте: `QUERY_ALL_PACKAGES` не используется в play-flavor (docs/reader-integration.md §2.4,
 * docs/architecture.md §9). Пустой результат — нормальная ситуация, а не ошибка.
 */
interface ReaderDiscovery {

    /** Установленные читалки. Пакеты, для которых нет профиля, оборачиваются в новый профиль. */
    suspend fun installedReaders(): List<InstalledReader>
}

/**
 * Найденная читалка.
 *
 * @param profile профиль читалки (встроенный preset или созданный из результата discovery).
 * @param resolveInfoLike краткое человекочитаемое описание цели (`ResolveInfo` →
 *   `activityInfo.packageName/name`); null, если описание недоступно. Строка, а не платформенный
 *   тип, — чтобы модуль не тянул платформенные объекты в доменный слой.
 */
data class InstalledReader(
    val profile: ReaderProfile,
    val resolveInfoLike: String?,
)

/**
 * Заглушка discovery на `PackageManager`.
 *
 * В Sprint 5 конструктор получит `@ApplicationContext` — иначе `queryIntentActivities` недоступен.
 */
@Singleton
class AndroidReaderDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderDiscovery {

    override suspend fun installedReaders(): List<InstalledReader> {
        val packageManager = context.packageManager
        val ownPackage = context.packageName

        // Пробный URI обязателен: читалки объявляют фильтры со схемой `content`/`file`, а
        // intent «только с типом» (без данных) с ними не совпадает. Проверено на устройстве:
        // `setDataAndType(null, mime)` не находил FBReader вообще, а тот же запрос с
        // `content://` находил — и читалку, и десяток посторонних приложений.
        //
        // Расширение в пути нужно, чтобы срабатывали и фильтры по `pathPattern`.
        fun probeFor(mime: String): Intent {
            val extension = MimeTypes.extensionsFor(mime).firstOrNull() ?: PROBE_EXTENSION
            return Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(
                    Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(PROBE_AUTHORITY)
                        .appendPath("probe.$extension")
                        .build(),
                    mime,
                )
            }
        }

        // Собираем activity только для проверенных читалок. Посторонние приложения, тоже
        // объявившие книжный MIME (браузеры, галереи, файловые менеджеры), сюда не попадают
        // вообще: пользователь выбирает читалку, а не «любое приложение, которое подошло
        // под запрос системы». Длинный список с браузерами был бесполезен и ошибочен.
        val byPackage = linkedMapOf<String, InstalledReaderBuilder>()
        MimeTypes.knownMimeTypes().forEach { mime ->
            packageManager.queryIntentActivities(probeFor(mime), 0).forEach { info ->
                val activity = info.activityInfo
                if (activity.packageName == ownPackage) return@forEach
                if (activity.packageName !in FbReaderPreset.KNOWN_READER_PACKAGES) return@forEach
                byPackage.getOrPut(activity.packageName) { InstalledReaderBuilder(activity.packageName) }
                    .also { builder -> builder.mimes.add(mime) }
                    .also { builder -> builder.activities.putIfAbsent(activity.name, true) }
            }
        }

        return byPackage.values.mapNotNull { builder ->
            val info = runCatching { packageManager.getApplicationInfo(builder.packageName, 0) }
                .getOrNull()
                ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(packageManager).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            InstalledReader(
                profile = ReaderProfile(
                    id = builder.packageName,
                    name = label,
                    packageName = builder.packageName,
                    // Имя activity, а НЕ ApplicationInfo.name: класс Application нельзя
                    // поставить целью intent. Раньше сюда попадало имя Application-класса,
                    // и запуск уходил в «не найдено приложение», хотя читалка была установлена.
                    activityName = builder.activities.keys.first(),
                    mimeTypes = builder.mimes.toList(),
                    supportsContentUri = true,
                    extrasJson = null,
                    isBuiltIn = false,
                ),
                resolveInfoLike = builder.activities.keys.first(),
            )
            // Порядок из PackageManager не переносим: выбор «первой подходящей» без ранга
            // зависел бы от версии Android. Впереди — текущий FBReader, за ним старый.
        }.sortedBy { FbReaderPreset.rankOf(it.profile.packageName) }
    }

    private companion object {
        /**
         * Домен пробного URI. Не существует — запрос ничего не открывает, он только спрашивает
         * у `PackageManager`, какие activity объявили фильтр на книжный MIME.
         */
        const val PROBE_AUTHORITY = "io.github.bookshelf.probe"

        /** Расширение, когда у MIME нет своего: нужно для фильтров по `pathPattern`. */
        const val PROBE_EXTENSION = "book"
    }

    /** Накопитель находок по одному пакету, пока листы запросов идут по MIME. */
    private class InstalledReaderBuilder(val packageName: String) {
        val mimes = mutableSetOf<String>()

        /** Имена activity, объявивших хотя бы один подходящий фильтр. */
        val activities = linkedMapOf<String, Boolean>()
    }
}
