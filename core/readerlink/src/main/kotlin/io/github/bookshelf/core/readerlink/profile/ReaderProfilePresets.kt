package io.github.bookshelf.core.readerlink.profile

/**
 * Встроенные presets читалок.
 *
 * FBReader — главная читалка проекта, поэтому его preset содержит все константы интеграции
 * из «Читалки»: action'ы открытия (L1), сервис API (L2) и сервис библиотеки (L3).
 * Константы объявлены своими (ADR-005): мы не вендорим код FBReader, а только фиксируем строки,
 * которые публично декларирует его манифест.
 *
 * Пакеты остальных читалок требуется подтвердить на живом устройстве в Sprint 0, поэтому в
 * заготовках [ReaderProfilePresets] они оставлены пустыми: выдуманный пакет хуже отсутствующего.
 */
object FbReaderPreset {

    const val ID: String = "fbreader"

    /**
     * Пакет FBReader.
     *
     * Проверено на устройстве (`dumpsys package com.fbreader`, 2026-09-25): текущая сборка
     * FBReader ставится как `com.fbreader`, а её activity — `com.fbreader.android.fbreader.FBReader`.
     * Прежнее значение `org.geometerplus.zlibrary.ui.android` — это пакет старого
     * FBReader for Android, которого на устройстве нет, поэтому запуск в него всегда
     * заканчивался «не найдено приложение».
     */
    const val PACKAGE_NAME: String = "com.fbreader"

    /** Основная activity FBReader (`launchMode="singleTask"`). */
    const val ACTIVITY_NAME: String = "com.fbreader.android.fbreader.FBReader"

    /**
     * Пакеты, которым отдаётся предпочтение при выборе читалки, по убыванию.
     *
     * Проверено на устройстве (2026-09-25): кроме FBReader там стоят `xyz.anilabx.app`
     * (AniLabX — приложение для просмотра аниме) и ещё несколько приложений, которые тоже
     * объявляют `ACTION_VIEW` с книжными MIME-типами. `PackageManager` отдаёт их первыми, и
     * кнопка «Читать» открывала книгу в AniLabX. AniLabX не читалка, поэтому FBReader должен
     * выигрывать явно, а не по порядку, который вернёт система.
     */
    val PREFERRED_PACKAGES: List<String> = listOf(
        PACKAGE_NAME,
        "org.geometerplus.zlibrary.ui.android",
    )

    /**
     * Пакеты известных читалок, которым приложение доверяет открывать книги.
     *
     * Имена взяты не из догадок, а из первоисточников (Google Play, F-Droid, сайты проектов),
     * и потому остаются проверяемыми:
     * * `com.fbreader` — FBReader 3.8.31, подтверждён `dumpsys package` на устройстве;
     * * `org.geometerplus.zlibrary.ui.android` — FBReader for Android из F-Droid;
     * * `org.coolreader` — CoolReader 3.2.26;
     * * `org.koreader.launcher` / `org.koreader.launcher.fdroid` — KOReader (две сборки);
     * * `com.foobnix.pdf.reader` — Librera;
     * * `com.neverland.alreader` — AlReader.
     *
     * Список закрытый: приложение, которого здесь нет, в «читалки» не попадёт, даже если оно
     * объявило книжный MIME-тип. Поэтому в списке не будет ни браузеров, ни файловых менеджеров.
     */
    val KNOWN_READER_PACKAGES: List<String> = listOf(
        PACKAGE_NAME,
        "org.geometerplus.zlibrary.ui.android",
        "org.coolreader",
        "org.koreader.launcher",
        "org.koreader.launcher.fdroid",
        "com.foobnix.pdf.reader",
        "com.neverland.alreader",
    )

    /** Системный VIEW. */
    const val ACTION_SYSTEM_VIEW: String = "android.intent.action.VIEW"

    /** «Родное» действие FBReader; исторически самый надёжный способ передать файл. */
    const val ACTION_VIEW: String = "android.fbreader.action.VIEW"

    /** L2: сервис API (AIDL `ApiInterface`). */
    const val ACTION_API: String = "android.fbreader.action.API"

    /** L3: сервис библиотеки (AIDL `LibraryInterface`). */
    const val ACTION_LIBRARY_SERVICE: String = "android.fbreader.action.LIBRARY_SERVICE"

    /** Широковещательное событие API; тип приходит в `int`-дополнительном поле `event.type`. */
    const val ACTION_API_CALLBACK: String = "android.fbreader.action.API_CALLBACK"

    /** Класс сервиса L2 — для диагностики и `bindService`. */
    const val API_SERVICE_NAME: String = "org.geometerplus.android.fbreader.api.ApiService"

    /** Класс сервиса L3 — для диагностики и `bindService`. */
    const val LIBRARY_SERVICE_NAME: String =
        "org.geometerplus.android.fbreader.libraryService.LibraryService"

    /** MIME-типы из манифеста FBReader, включая нестандартный `application/x-fictionbook+xml`. */
    val MIME_TYPES: List<String> = listOf(
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/x-fictionbook+xml",
        "application/x-fictionbook",
        "application/pdf",
        "application/x-pdf",
        "application/djvu",
        "application/x-djvu",
        "image/vnd.djvu",
        "image/x-djvu",
        "application/x-cbr",
        "application/x-cbz",
        "text/plain",
    )

    /** Расширения, которые FBReader принимает по `pathPattern` («Читалки» §2.1). */
    val EXTENSIONS: List<String> = listOf(
        "fb2",
        "fb2.zip",
        "epub",
        "mobi",
        "prc",
        "azw3",
        "txt",
        "rtf",
        "pdf",
        "djvu",
        "cbr",
        "cbz",
    )

    private val extrasJson: String = """
        {
          "viewActions": ["android.intent.action.VIEW", "android.fbreader.action.VIEW"],
          "apiAction": "android.fbreader.action.API",
          "libraryServiceAction": "android.fbreader.action.LIBRARY_SERVICE",
          "apiCallbackAction": "android.fbreader.action.API_CALLBACK",
          "uriPreference": ["CONTENT_URI_WITH_GRANT", "FILE_URI", "FILE_PROVIDER", "TEMP_COPY"],
          "progressRead": true,
          "progressWrite": true,
          "coverFallback": true,
          "openAtPosition": true,
          "verifiedInSprint0": false
        }
    """.trimIndent()

    /** Готовый preset FBReader. */
    val PROFILE: ReaderProfile = ReaderProfile(
        id = ID,
        name = "FBReader",
        packageName = PACKAGE_NAME,
        activityName = ACTIVITY_NAME,
        mimeTypes = MIME_TYPES,
        supportsContentUri = true,
        extrasJson = extrasJson,
        isBuiltIn = true,
        enabled = true,
        priority = 1,
    )

    /**
     * Ранг пакета для выбора «если подходящих несколько»: меньше — раньше.
     *
     * Пакеты вне списка идут после, сортировка по имени — иначе выбор читалки зависел бы от
     * внутреннего порядка `PackageManager`, который меняется от версии к версии Android.
     */
    fun rankOf(packageName: String): Int {
        val index = PREFERRED_PACKAGES.indexOf(packageName)
        return if (index >= 0) index else PREFERRED_PACKAGES.size
    }
}

/**
 * Каталог встроенных профилей.
 *
 * Здесь лежат только presets, не зависящие от установленных приложений. Профили, найденные
 * через `PackageManager`, добавляет discovery (Sprint 5), а пользовательские — экран «Читалки».
 */
object ReaderProfilePresets {

    // STUB(Sprint 0): подтвердить пакет KOReader на живом устройстве; в «Читалки»
    // §5 указан кандидат org.koreader.launcher — как непроверенный он намеренно не проставлен.

    /** Заготовка KOReader: пакет и MIME пусты до проверки в Sprint 0. */
    private val koReader: ReaderProfile = ReaderProfile(
        id = "koreader",
        name = "KOReader",
        packageName = "",
        activityName = null,
        mimeTypes = emptyList(),
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = true,
        enabled = false,
    )

    // STUB(Sprint 0): найти и подтвердить пакет AlReader на живом устройстве.

    /** Заготовка AlReader: пакет неизвестен, профиль выключен. */
    private val alReader: ReaderProfile = ReaderProfile(
        id = "alreader",
        name = "AlReader",
        packageName = "",
        activityName = null,
        mimeTypes = emptyList(),
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = true,
        enabled = false,
    )

    // STUB(Sprint 0): подтвердить пакет Librera на живом устройстве; в «Читалки»
    // §5 указан кандидат com.foobnix.pdf.info — как непроверенный он намеренно не проставлен.

    /** Заготовка Librera: пакет и MIME пусты до проверки в Sprint 0. */
    private val librera: ReaderProfile = ReaderProfile(
        id = "librera",
        name = "Librera",
        packageName = "",
        activityName = null,
        mimeTypes = emptyList(),
        supportsContentUri = true,
        extrasJson = null,
        isBuiltIn = true,
        enabled = false,
    )

    /** Все встроенные presets; FBReader идёт первым. */
    val all: List<ReaderProfile> = listOf(FbReaderPreset.PROFILE, koReader, alReader, librera)

    /** Preset по [ReaderProfile.id] или null, если такого нет. */
    fun byId(id: String): ReaderProfile? = all.firstOrNull { it.id == id }
}
