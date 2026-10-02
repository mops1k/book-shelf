package io.github.bookshelf.core.readerlink.discovery

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * манифесте: `QUERY_ALL_PACKAGES` не используется в play-flavor («Читалки» §2.4,
 * «Архитектура» §9). Пустой результат — нормальная ситуация, а не ошибка.
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

        val found = linkedMapOf<String, InstalledReaderBuilder>()
        MimeTypes.probeMimeTypes().forEach { mime ->
            // Флаг `GET_RESOLVED_FILTER` обязателен: без него `ResolveInfo.filter` пуст, и
            // «объявленные книжные форматы» получаются пустыми — читалка вне списка известных
            // снова пропадала (проверено на устройстве: XReader исчез, остался только FBReader).
            packageManager.queryIntentActivities(probeFor(mime), PackageManager.GET_RESOLVED_FILTER)
                .forEach { info ->
                val activity = info.activityInfo
                if (activity.packageName == ownPackage) return@forEach
                // Решение принимается по ОБЪЯВЛЕННЫМ типам (`info.filter`), а не по тому, чем
                // приложение поймало запрос: общий шаблон ловит всё, и читалкой стало бы всё —
                // браузер, «Фото», словарь и редактор документов.
                val declaredBook = declaredBookMimeTypes(info.filter)
                if (readerRankOf(activity.packageName, declaredBook) == ReaderRank.OTHER) return@forEach
                // В профиль пишем ВСЕ объявленные типы, а не только книжные: иначе читалка,
                // умеющая PDF, не попала бы в список «Документы».
                val declared = declaredMimeTypes(info.filter)
                found.getOrPut(activity.packageName) { InstalledReaderBuilder(activity.packageName) }
                    .also { builder -> builder.mimes.addAll(declared) }
                    .also { builder -> builder.activities.putIfAbsent(activity.name, true) }
            }
        }
        return found.values.toReaders(packageManager)
    }

    /**
     * Профили из найденных приложений: человеческое имя, activity и порядок.
     *
     * Вынесено из [installedReaders] отдельной функцией: сборка списка и сам обход —
     * разные заботы, а вместе они упирались в порог `CyclomaticComplexMethod`.
     */
    private fun Collection<InstalledReaderBuilder>.toReaders(
        packageManager: PackageManager,
    ): List<InstalledReader> = mapNotNull { builder ->
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
    // Порядок из PackageManager не переносим: выбор «первой подходящей» без ранга зависел бы
    // от версии Android. Впереди — известные читалки (в порядке предпочтения), за ними те,
    // кто объявил книжные форматы.
    }.sortedWith(
        compareBy<InstalledReader> { reader ->
            when (readerRankOf(reader.profile.packageName, reader.profile.mimeTypes)) {
                ReaderRank.KNOWN -> FbReaderPreset.rankOf(reader.profile.packageName)
                ReaderRank.BOOK_FORMAT -> KNOWN_READERS_WEIGHT
                ReaderRank.OTHER -> OTHER_APPS_WEIGHT
            }
        }.thenBy { reader -> reader.profile.name.lowercase() },
    )

    private companion object {
        /**
         * Домен пробного URI. Не существует — запрос ничего не открывает, он только спрашивает
         * у `PackageManager`, какие activity объявили фильтр на книжный MIME.
         */
        const val PROBE_AUTHORITY = "io.github.bookshelf.probe"

        /** Расширение, когда у MIME нет своего: нужно для фильтров по `pathPattern`. */
        const val PROBE_EXTENSION = "book"

        /** Вес «читалка, найденная по книжному формату»: после всех известных, до остальных. */
        const val KNOWN_READERS_WEIGHT = 1_000

        /** Вес остальных приложений: они в конце списка, но человек их видит и может выбрать. */
        const val OTHER_APPS_WEIGHT = 2_000
    }

    /** Накопитель находок по одному пакету, пока листы запросов идут по MIME. */
    private class InstalledReaderBuilder(val packageName: String) {
        val mimes = mutableSetOf<String>()

        /** Имена activity, объявивших хотя бы один подходящий фильтр. */
        val activities = linkedMapOf<String, Boolean>()
    }
}
