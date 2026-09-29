package io.github.bookshelf.core.opds.download

import java.util.Locale

/**
 * Относительный путь скачанного файла по шаблону из настроек (`opds.downloadFileNameTemplate`).
 *
 * Шаблон хранится строкой, потому что это настройка, а не перечисление; здесь же лежит список
 * готовых вариантов — по нему строятся чипы на экране настроек, поэтому экран не может показать
 * вариант, которого не понимает загрузчик.
 *
 * **Слэш в шаблоне — это папка.** «{author}/{title}.{ext}» кладёт книгу в подпапку автора внутри
 * папки-источника: получается та же раскладка, что у людей в файловом менеджере, и следующий скан
 * подхватывает файлы как обычные книги. Промежуточные папки создаёт `SafTreeWriter`.
 *
 * Заполнители:
 * * `{author}` — первый автор (в OPDS запись может иметь нескольких; для пути достаточно первого,
 *   иначе имя превращается в список);
 * * `{series}` — серия; если каталог её не сообщил, пустая часть отбрасывается вместе с папкой;
 * * `{title}` — название;
 * * `{ext}` — расширение, возможно составное (`fb2.zip`).
 *
 * Значения подставляются только после очистки: слэш внутри названия книги не должен внезапно
 * создать папку, а двоеточие или звёздочка — сломать путь на некоторых провайдерах.
 */
val DOWNLOAD_FILE_NAME_TEMPLATES: List<String> = listOf(
    DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE,
    "{author}/{title}.{ext}",
    "{author}/{series}/{title}.{ext}",
    "{title}.{ext}",
)

/** Умолчание шаблона; совпадает с `SettingsKeys.Defaults.DOWNLOAD_FILE_NAME_TEMPLATE`. */
const val DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE: String = "{author} - {title}.{ext}"

/**
 * Подставить значения в шаблон и получить относительный путь.
 *
 * Гарантии, на которые опирается загрузчик:
 * * в результате есть расширение (по нему приложение выбирает парсер, а провайдеры требуют
 *   осмысленное имя), даже если в шаблоне не было `{ext}`;
 * * сегменты пути очищены от запрещённых символов, пустые — отброшены, поэтому «{author}/{title}»
 *   без автора даёт «Название.fb2», а не «/Название.fb2»;
 * * результат никогда не пуст: в крайнем случае это «book.{ext}».
 */
fun renderDownloadRelativePath(
    template: String,
    title: String,
    authors: List<String>,
    series: String?,
    extension: String,
): String {
    val substituted = template
        .replace(AUTHOR_PLACEHOLDER, sanitizePathSegment(authors.firstOrNull().orEmpty()))
        .replace(SERIES_PLACEHOLDER, sanitizePathSegment(series.orEmpty()))
        .replace(TITLE_PLACEHOLDER, sanitizePathSegment(title))
        .replace(EXTENSION_PLACEHOLDER, sanitizePathSegment(extension))
        .replace(REPEATED_SPACES, " ")

    val segments = substituted
        .split(PATH_SEPARATOR)
        .map { segment -> tidySegment(segment, extension) }
        .filter { segment -> segment.isNotEmpty() }

    if (segments.isEmpty()) return "book.$extension"

    val directories = segments.dropLast(1)
    val fileName = ensureExtension(segments.last(), extension)
    return (directories + fileName).joinToString(PATH_SEPARATOR)
}

/**
 * Дописать расширение, если шаблон его не дал.
 *
 * Сравнение без учёта регистра: «.FB2» уже расширение, и второй раз приписывать нечего.
 */
private fun ensureExtension(fileName: String, extension: String): String {
    val suffix = ".${extension.lowercase(Locale.ROOT)}"
    return if (fileName.lowercase(Locale.ROOT).endsWith(suffix)) fileName else fileName + suffix
}

/**
 * Убрать пустые части сегмента вместе с их разделителями.
 *
 * «{author} - {title}» без автора даёт « - Название», то есть файл, начинающийся с дефиса; пустая
 * часть выбрасывается, и остаётся «Название». Если от сегмента не осталось ничего, кроме
 * расширения (в шаблоне серия есть, а её значение пустое), сегмент тоже отбрасывается — иначе
 * появилась бы папка «.fb2».
 */
private fun tidySegment(segment: String, extension: String): String {
    val tidied = segment
        .split(DASH_SEPARATOR)
        .map { part -> part.trim() }
        .filter { part -> part.isNotEmpty() && part != ".$extension" }
        .joinToString(DASH_SEPARATOR)
        .trim()
    return tidied
}

/** Очистка значения: запрещены разделители пути, управляющие символы и слишком длинные имена. */
private fun sanitizePathSegment(value: String): String = value
    .filterNot { char -> char.isISOControl() || char in FORBIDDEN_IN_NAME }
    .trim()
    .take(MAX_SEGMENT_LENGTH)

private const val AUTHOR_PLACEHOLDER = "{author}"
private const val SERIES_PLACEHOLDER = "{series}"
private const val TITLE_PLACEHOLDER = "{title}"
private const val EXTENSION_PLACEHOLDER = "{ext}"
private const val PATH_SEPARATOR = "/"
private const val DASH_SEPARATOR = " - "
private const val MAX_SEGMENT_LENGTH = 120

private val REPEATED_SPACES = Regex("\\s{2,}")

/**
 * Что нельзя вернуть провайдеру в имени папки или файла.
 *
 * Кириллица, «№», кавычки и тире у названий встречаются постоянно и сохраняются: прежняя жёсткая
 * фильтрация превращала «Полдень XXI век 2002 №3» в «Полдень XXI век 2002 _3».
 */
private val FORBIDDEN_IN_NAME = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
