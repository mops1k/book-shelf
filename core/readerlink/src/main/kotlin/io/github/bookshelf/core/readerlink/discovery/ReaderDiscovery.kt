package io.github.bookshelf.core.readerlink.discovery

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
class AndroidReaderDiscovery @Inject constructor() : ReaderDiscovery {

    // STUB(Sprint 5): построить Intent(ACTION_VIEW) на каждый MIME из presets, вызвать
    // packageManager.queryIntentActivities(intent, 0) и сопоставить пакеты с профилями.

    override suspend fun installedReaders(): List<InstalledReader> =
        TODO("STUB(Sprint 5): queryIntentActivities + <queries> вместо QUERY_ALL_PACKAGES")
}
