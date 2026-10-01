package io.github.bookshelf.core.storage.volume

/**
 * Съёмный носитель, подключённый к устройству: карта памяти или флешка.
 *
 * @param uuid идентификатор тома (`StorageVolume.getUuid`): по нему носитель связывается с
 *   источником книг, поэтому том без UUID носителем не считается.
 * @param label метка, как её называет система; пустая — имя даёт экран настроек.
 * @param path путь монтирования: по нему читается том, когда приложение имеет доступ ко всем файлам.
 */
data class RemovableVolume(
    val uuid: String,
    val label: String,
    val path: String,
)

/**
 * Том в том виде, в каком его видит система.
 *
 * Отдельный тип нужен, чтобы правило «что считать носителем» проверялось тестом без Android:
 * `StorageVolume` в юнит-тесте не собрать, а ошибиться здесь дорого — полка «Карта памяти»
 * появлялась бы при встроенной памяти или не появлялась при настоящей карте.
 */
data class RawVolume(
    val uuid: String?,
    val label: String?,
    val path: String?,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
    val isMounted: Boolean,
)

/**
 * Отобрать съёмные носители из томов системы.
 *
 * Требования к тому: он съёмный, не эмулированный (встроенная память — не носитель), подключён
 * (`Environment.MEDIA_MOUNTED`) и имеет и UUID, и путь. Том без UUID пропускается: связать с ним
 * источник нельзя, и полка «Карта памяти» осталась бы на экране после изъятия носителя.
 * Метка не подменяется: пустую называет экран, потому что имя носителя — данные системы, а не наш
 * текст.
 */
fun removableVolumesOf(raw: List<RawVolume>): List<RemovableVolume> = raw
    .filter { volume ->
        volume.isRemovable &&
            !volume.isEmulated &&
            volume.isMounted &&
            !volume.uuid.isNullOrBlank() &&
            !volume.path.isNullOrBlank()
    }
    .map { volume ->
        RemovableVolume(
            uuid = checkNotNull(volume.uuid).trim(),
            label = volume.label?.trim().orEmpty(),
            path = checkNotNull(volume.path).trim(),
        )
    }
