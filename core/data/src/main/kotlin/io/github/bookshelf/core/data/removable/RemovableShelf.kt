package io.github.bookshelf.core.data.removable

import io.github.bookshelf.core.storage.volume.RemovableVolume

/**
 * Полка съёмного носителя: что показывать в списке полок.
 *
 * @param volume носитель, который сейчас подключён.
 * @param sourceId источник книг носителя; `null` — доступа ещё нет, книги не собраны.
 * @param bookCount книг носителя в библиотеке; ноль — носитель подключён, но книг на нём не нашли
 *   (или доступ ещё не выдан).
 * @param needsAccess нужно разрешение человека: доступ ко всем файлам не выдан, а источник ещё не
 *   заведён. Экран объясняет это и предлагает дать доступ.
 */
data class RemovableShelf(
    val volume: RemovableVolume,
    val sourceId: String?,
    val bookCount: Int,
    val needsAccess: Boolean,
)

/** Как приложение получает доступ к книгам носителя. */
enum class CardAccess {
    /** Доступ ко всем файлам уже выдан: источник заводится сам, вопроса человеку не задаём. */
    DIRECT_PATH,

    /** Доступа нет: системный выбор папки спросит человека один раз. */
    SAF_TREE,
}

/** Доступ ко всем файлам есть — прямое подключение, иначе спрашиваем дерево у человека. */
fun cardAccessOf(hasAllFilesAccess: Boolean): CardAccess =
    if (hasAllFilesAccess) CardAccess.DIRECT_PATH else CardAccess.SAF_TREE

/** Дерево корня тома: адрес для системного провайдера (в нём UUID с экранированным двоеточием). */
fun volumeTreeUri(uuid: String): String =
    "$EXTERNAL_STORAGE_TREE_PREFIX${uuid.trim().uppercase()}$DOCUMENT_ID_SEPARATOR_ESCAPED"

/**
 * Относится ли источник к этому носителю.
 *
 * Узнаём по UUID внутри адреса источника: адрес SAF-дерева хранит его в `documentId`
 * (`tree/1A2B-3C4D%3A`), прямой путь — в самом пути (`/storage/1A2B-3C4D/...`). Двоеточие в адресе
 * может быть экранировано (`%3A`) или нет — проверяются оба вида, как и регистр: система отдаёт
 * UUID в разном виде на разных прошивках.
 *
 * Встроенная память (`primary:`) под это правило не подходит никогда: её `documentId` не содержит
 * UUID карты, поэтому «Карта памяти» не появится из-за основной папки библиотеки.
 */
fun sourceBelongsToVolume(sourceUri: String, uuid: String): Boolean {
    val wanted = uuid.trim()
    if (wanted.isBlank() || sourceUri.isBlank()) return false
    val decoded = sourceUri.replace(DOCUMENT_ID_SEPARATOR_ESCAPED, ":", ignoreCase = true)
    val marker = wanted.lowercase()
    return decoded.lowercase().let { address ->
        address.contains("tree/$marker") ||
            address.contains("/$marker") ||
            address.contains("$marker:")
    }
}

/**
 * UUID съёмного тома внутри адреса источника или `null`, если адрес к съёмному тому не относится.
 *
 * Нужно, чтобы отличить «источник съёмного носителя, которого сейчас нет» от обычной папки: у папки
 * UUID тома в адресе не встречается (`primary:Books`), и помечать её книги пропавшими нельзя.
 * Формат UUID тома в Android — восемь шестнадцатеричных цифр через дефис (`1A2B-3C4D`).
 */
fun volumeUuidOf(sourceUri: String): String? =
    VOLUME_UUID_PATTERN.find(sourceUri.replace(DOCUMENT_ID_SEPARATOR_ESCAPED, ":", ignoreCase = true))
        ?.value
        ?.uppercase()

private val VOLUME_UUID_PATTERN = Regex("""[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}""")

private const val EXTERNAL_STORAGE_TREE_PREFIX =
    "content://com.android.externalstorage.documents/tree/"
private const val DOCUMENT_ID_SEPARATOR_ESCAPED = "%3A"
