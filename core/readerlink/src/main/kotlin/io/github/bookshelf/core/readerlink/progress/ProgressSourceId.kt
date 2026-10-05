package io.github.bookshelf.core.readerlink.progress

/**
 * Ключи каналов прогресса — значения колонки `reading_progress.source`.
 *
 * Строки живут здесь, а не в `:core:data`, потому что их источник — сам источник прогресса: читалка,
 * которая прогресс отдала. `ReadingProgressOrigins` в `:core:data` ссылается на эти константы, так
 * что значение в базе и разбор в приложении не могут разойтись.
 *
 * Менять существующие значения нельзя: они уже лежат в базе у пользователей, и по ним полка и
 * диагностика отличают один канал от другого. Новый канал — новая константа.
 */
object ProgressSourceKeys {

    /** API-сервис FBReader (AIDL, `com.fbreader.action.API`). */
    const val FBREADER_AIDL = "FBREADER_AIDL"

    /** Sidecar-файл KOReader (`<книга>.sdr/metadata.<расширение>.lua`) рядом с книгой. */
    const val KOREADER_SIDECAR = "KOREADER_SIDECAR"

    /** База CoolReader `/sdcard/.cr3/cr3db.sqlite`. */
    const val COOLREADER_DB = "COOLREADER_DB"

    /** База PocketBook `/sdcard/.pocketbook/books.db`. */
    const val POCKETBOOK_DB = "POCKETBOOK_DB"

    /** JSON-профиль Librera `…/profile.Librera/device.<модель>/app-Progress.json`. */
    const val LIBRERA_JSON = "LIBRERA_JSON"

    /** Кэш XReader `/sdcard/XRZBookCache/V3/js<id>`. */
    const val XREADER_CACHE = "XREADER_CACHE"
}

/**
 * Кто отдал прогресс чтения.
 *
 * Порядок объявления задаёт порядок обхода источников в фоновом проходе: он должен быть
 * детерминированным, иначе журнал и диагностика зависели бы от порядка биндингов в DI.
 *
 * Объявлены и каналы, которых пока нет в коде: значения нужны настройкам и `reading_progress.source`
 * раньше, чем сами источники (см. план внедрения по читалкам). Пока источник не реализован, его
 * нельзя включить, и в реестре он не появится.
 */
enum class ProgressSourceId(val storageKey: String) {

    /** FBReader: единственная читалка с публичным API (уровень L2). */
    FBREADER_AIDL(ProgressSourceKeys.FBREADER_AIDL),

    /** KOReader: sidecar лежит рядом с книгой, поэтому доступен и по SAF. */
    KOREADER_SIDECAR(ProgressSourceKeys.KOREADER_SIDECAR),

    /** CoolReader: прогресс в SQLite на общей памяти (нужен доступ ко всем файлам). */
    COOLREADER_DB(ProgressSourceKeys.COOLREADER_DB),

    /** PocketBook: прогресс в тегах своей SQLite на общей памяти. */
    POCKETBOOK_DB(ProgressSourceKeys.POCKETBOOK_DB),

    /** Librera: прогресс в JSON-профиле на общей памяти. */
    LIBRERA_JSON(ProgressSourceKeys.LIBRERA_JSON),

    /** XReader (встроенная читалка Bigme): прогресс в gzip-кэше на общей памяти. */
    XREADER_CACHE(ProgressSourceKeys.XREADER_CACHE),
}
