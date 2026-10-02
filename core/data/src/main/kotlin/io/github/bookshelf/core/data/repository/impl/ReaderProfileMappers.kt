package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.database.entity.ReaderProfileEntity
import io.github.bookshelf.core.readerlink.profile.ReaderProfile

/**
 * Профиль читалки: строка таблицы `reader_profiles` ⇄ доменная модель.
 *
 * Списки MIME в таблице хранятся одной строкой: модуль БД про JSON и списки ничего не знает.
 * Разделитель — запятая: MIME-тип её не содержит.
 */
internal fun ReaderProfileEntity.toProfile(): ReaderProfile = ReaderProfile(
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

internal fun ReaderProfile.toEntity(): ReaderProfileEntity = ReaderProfileEntity(
    id = id,
    name = name,
    packageName = packageName,
    activityName = activityName,
    mimeTypes = mimeTypes.joinToString(MIME_SEPARATOR),
    supportsContentUri = supportsContentUri,
    extrasJson = extrasJson,
    isBuiltIn = isBuiltIn,
    enabled = enabled,
    priority = priority,
)

/** Разделитель списка MIME в строковом поле. */
internal const val MIME_SEPARATOR = ","
