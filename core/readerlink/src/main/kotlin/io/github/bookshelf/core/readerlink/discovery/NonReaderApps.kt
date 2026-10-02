package io.github.bookshelf.core.readerlink.discovery

import android.content.IntentFilter

/**
 * Чёрный список не-читалок: приложения, которые умеют открывать книги, но читалками не являются.
 *
 * Требование Александра (01.10.2026): «фото, дик, браузеры, html надо добавить в исключения и не
 * выводить их в списке, а также приложения редактирования документов и т.д.», с уточнением —
 * «надо вести не белый список читалок, а чёрный список не читалок».
 *
 * Почему не хватает одного признака «объявил книжный формат»: словарь `com.eusoft.eudic`
 * объявляет `application/epub+zip` ЧЕСТНО (замер с устройства) и всё равно читалкой не является.
 * Поэтому к правилу по типам добавлен список известных не-читалок.
 *
 * Список сознательно короткий и по категориям: браузеры, галереи, просмотрщики HTML, словари и
 * переводчики, офисные редакторы. Читалка сюда попасть не должна — иначе человек потеряет выбор,
 * как это уже случилось с белым списком, из-за которого пропадал XReader.
 */
object NonReaderApps {

    /** Точные имена пакетов: те, что встречаются на целевых прошивках. */
    val PACKAGES: Set<String> = setOf(
        // Браузеры.
        "com.brave.browser",
        "com.android.chrome",
        "com.chrome.beta",
        "org.mozilla.firefox",
        "org.mozilla.firefox.market",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.yandex.browser",
        "com.sec.android.app.sbrowser",
        // Галереи и «Фото».
        "com.google.android.apps.photos",
        "com.android.gallery3d",
        "com.miui.gallery",
        "com.coloros.gallery3d",
        // Просмотрщик HTML и текстовый просмотрщик системы.
        "com.android.htmlviewer",
        "com.android.documentsui",
        // Синтезаторы речи: книгу они читают вслух, но читалкой не являются
        // (Александр, 01.10.2026: «voxsherpa тоже в чёрный список, это движок tts»).
        "com.CodeBySonu.VoxSherpa",
        // Словари и переводчики.
        "com.eusoft.eudic",
        "com.eusoft.eudic.pro",
        "com.google.android.apps.translate",
        "ru.yandex.translate",
        // Офисные редакторы: документы, а не книги.
        "com.microsoft.office.word",
        "com.microsoft.office.officehubrow",
        "com.google.android.apps.docs",
        "cn.wps.moffice_eng",
        "com.onlyoffice.documents",
        "org.libreoffice",
    )

    /**
     * Приметы в имени пакета: новое приложение той же породы попадёт в список само.
     *
     * Без «reader», «book», «fb2» и подобных слов: они как раз про читалки. Ловим только те
     * слова, которые в имени читалки не встречаются.
     */
    val PACKAGE_MARKERS: List<String> = listOf(
        "browser",
        "gallery",
        "htmlviewer",
        "translate",
        "dictionary",
        "msoffice",
        // Синтезаторы речи: в имени читалки «tts» не встречается, а у движка — есть.
        "tts",
        "moffice",
        "office",
        "photos",
    )

    /** Известная не-читалка: исключается из списка выбора. */
    fun isKnownNonReader(packageName: String): Boolean {
        val name = packageName.lowercase()
        if (packageName in PACKAGES) return true
        return PACKAGE_MARKERS.any { marker -> marker in name }
    }
}

/**
 * Книжные типы, объявленные фильтром ЯВНО.
 *
 * Общий шаблон (звёздочка) и текстовые/графические типы доказательством не считаются: так
 * объявляют себя браузер, «Фото» и просмотрщик HTML — они тоже отвечают на запрос о книге.
 *
 * @param filter фильтр, которым приложение поймало запрос (`ResolveInfo.filter`); `null` — доказательств нет.
 */
fun declaredBookMimeTypes(filter: IntentFilter?): Set<String> =
    declaredMimeTypes(filter).filterTo(mutableSetOf()) { type -> type in BOOK_MIME_EVIDENCE }

/**
 * Все типы, объявленные фильтром явно (без общих шаблонов).
 *
 * Нужны, чтобы показать, какие форматы читалка умеет: FBReader объявляет `application/pdf`, и файл
 * PDF должен предлагаться к привязке, хотя доказательством «это читалка» pdf не является.
 */
fun declaredMimeTypes(filter: IntentFilter?): Set<String> {
    if (filter == null) return emptySet()
    val declared = mutableSetOf<String>()
    val iterator = filter.typesIterator() ?: return emptySet()
    while (iterator.hasNext()) {
        val type = iterator.next()?.trim()?.lowercase().orEmpty()
        // Общие шаблоны (`*`, `text`, `image`) объявлением формата не считаются: их пишут все.
        if (type.isEmpty() || type.contains('*')) continue
        declared += type
    }
    return declared
}

/**
 * Типы, по которым приложение считается читалкой.
 *
 * Документы (docx, odt, rtf) здесь НЕ перечислены: их открывает и офисный редактор, а книги —
 * нет. Аналогично `pdf` и `text/plain`: их понимает почти всё.
 */
private val BOOK_MIME_EVIDENCE: Set<String> = setOf(
    "application/epub+zip",
    "application/epub",
    "application/fb2",
    "application/x-fb2",
    "application/fb2+zip",
    "application/x-fictionbook",
    "application/x-fictionbook+xml",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.ebook",
    "application/vnd.amazon.mobi8-ebook",
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/x-cbz",
    "application/x-cbr",
    "image/vnd.djvu",
    "application/x-djvu",
    "application/djvu",
)
