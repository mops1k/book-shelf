package io.github.bookshelf.work

/**
 * Человеческий текст ошибки загрузки книги из OPDS.
 *
 * Появился после дефекта 29.09.2026: провайдер отказывал в записи в папку, а пользователь видел
 * сырое сообщение вида
 * `Провайдер не создал 'Автор - Книга.fb2.zip' … Permission Denial: writing … requires
 * android.permission.MANAGE_DOCUMENTS, or grantUriPermission()`
 * — из которого невозможно понять, что делать.
 *
 * Отказ в записи — единственный случай, который стоит переводить: остальные ошибки (сеть, код
 * ответа, разбор) и так понятны по тексту, и переписывать их своими словами значило бы терять
 * подробности, по которым ищут причину.
 */
internal fun downloadErrorMessage(raw: String?): String {
    if (raw.isNullOrBlank()) return DEFAULT_DOWNLOAD_ERROR
    if (PERMISSION_DENIAL_MARKERS.any { marker -> raw.contains(marker, ignoreCase = true) }) {
        return "Приложению не разрешено писать в папку. Откройте «Настройки» → " +
            "«Библиотека и сканирование» и выберите папку заново — приложению нужно право записи."
    }
    return raw
}

private const val DEFAULT_DOWNLOAD_ERROR = "Не удалось скачать книгу"

/** Признаки отказа в доступе: так отвечает `ExternalStorageProvider`, когда нет права записи. */
private val PERMISSION_DENIAL_MARKERS = listOf(
    "Permission Denial",
    "MANAGE_DOCUMENTS",
    "grantUriPermission",
)
