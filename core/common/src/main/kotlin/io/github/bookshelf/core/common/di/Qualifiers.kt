package io.github.bookshelf.core.common.di

import javax.inject.Qualifier

/**
 * Квалификаторы Hilt для диспетчеров и других «безымянных» зависимостей.
 *
 * Держим их в `core:common`, чтобы любой модуль мог попросить `@IoDispatcher` без зависимости
 * на DI-граф приложения.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Диспетчер с ограниченным параллелизмом для парсеров и обложек. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MetadataDispatcher

/** Общий [kotlinx.coroutines.CoroutineScope] уровня приложения. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Каталог `filesDir/covers` и прочие «крупные» кэши на диске. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CoverCacheDir

/** Каталог `cacheDir/import` для временных копий и `cacheDir/export` для FileProvider. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TempDir

/** Каталог `cacheDir/export`, отдаваемый читалкам через FileProvider. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExportDir

/**
 * Каталог для тел фидов OPDS.
 *
 * Отдельный от [CoverCacheDir] намеренно: чистка обложек не должна сносить кэш каталогов,
 * а очистка каталогов — обложки.
 */
@Qualifier
annotation class OpdsCacheDir
