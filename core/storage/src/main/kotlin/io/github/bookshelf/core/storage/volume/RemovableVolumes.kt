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
    /**
     * Путь, по которому том читается сейчас, или `null`, если такого пути нет.
     *
     * Доступность определяет тот, кто видит файловую систему (монитор): чистому правилу остаётся
     * решить, носитель это или нет.
     */
    val path: String?,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
)

/**
 * Отобрать съёмные носители из томов системы.
 *
 * Требования к тому: он съёмный, не эмулированный (встроенная память — не носитель), имеет UUID и
 * доступный путь. Том без UUID пропускается: связать с ним источник нельзя, и полка «Карта памяти»
 * осталась бы на экране после изъятия носителя. Путь приходит уже проверенным на доступность.
 * Метка не подменяется: пустую называет экран, потому что имя носителя — данные системы, а не наш
 * текст.
 */
fun removableVolumesOf(raw: List<RawVolume>): List<RemovableVolume> = raw
    .filter { volume ->
        volume.isRemovable &&
            !volume.isEmulated &&
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

/** Приватный корень монтирования съёмных томов (`vold`): `/mnt/media_rw/<uuid>`. */
private const val PRIVATE_MOUNT_ROOT = "/mnt/media_rw"

/**
 * Кандидаты пути тома в порядке предпочтения: сначала публичный, потом приватный.
 *
 * Публичный путь (`/storage/<uuid>`) система публикует приложениям и файловым менеджерам, поэтому
 * он и предпочтителен. Приватный (`/mnt/media_rw/<uuid>`) — точка, куда монтирует `vold`: на
 * исправной прошивке он не нужен, но есть прошивки, которые публичную точку не создают вовсе
 * (Bigme B6 Color, проверено 02.10.2026), и тогда том читается только так — и только приложением
 * с доступом ко всем файлам.
 *
 * Пустой публичный путь не добавляется, приватный без UUID не угадывается; одинаковые пути не
 * дублируются.
 */
fun volumePathCandidates(uuid: String?, publicPath: String?): List<String> {
    val trimmedUuid = uuid?.trim().orEmpty()
    val candidates = listOfNotNull(
        publicPath?.trim()?.takeIf { it.isNotEmpty() },
        trimmedUuid.takeIf { it.isNotEmpty() }?.let { value -> "$PRIVATE_MOUNT_ROOT/$value" },
    )
    return candidates.distinct()
}

/**
 * Путь, по которому том читается: первый доступный кандидат, иначе `null`.
 *
 * Доступность проверяет вызывающий (это работа с файловой системой), правило остаётся чистым и
 * проверяется тестом без Android: именно здесь решается, что публичный путь важнее приватного.
 */
fun availableVolumePath(candidates: List<String>, isAvailable: (String) -> Boolean): String? =
    candidates.firstOrNull { candidate -> isAvailable(candidate) }
