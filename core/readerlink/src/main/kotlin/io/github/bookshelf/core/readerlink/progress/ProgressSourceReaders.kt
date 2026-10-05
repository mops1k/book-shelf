package io.github.bookshelf.core.readerlink.progress

/**
 * Пакеты читалок, которыми источник читает прогресс.
 *
 * Нужны, чтобы понять, **установлена ли читалка**: требование Александра (06.10.2026) — удалённой с
 * устройства читалки не должно быть видно в приложении нигде, в том числе в переключателях «Прогресс
 * из …». Пакеты подтверждены на устройстве (`pm list packages`): CoolReader — `org.coolreader`,
 * KOReader — `org.koreader.launcher` (есть и сборка `.fdroid`), PocketBook — `com.obreey.reader`,
 * XReader — `com.xrz.xreaderV3`, Librera — `com.foobnix.pdf.reader` и `com.foobnix.pro.pdf.reader`
 * (на устройстве стояла вторая).
 *
 * У источника может быть несколько пакетов: читалка ставится в разных сборках, и любая из них значит,
 * что источник рабочий.
 */
fun readerPackagesOf(source: ProgressSourceId): Set<String> = when (source) {
    ProgressSourceId.FBREADER_AIDL -> setOf(FBREADER_PACKAGE)
    ProgressSourceId.KOREADER_SIDECAR -> setOf("org.koreader.launcher", "org.koreader.launcher.fdroid")
    ProgressSourceId.COOLREADER_DB -> setOf(COOLREADER_PACKAGE)
    ProgressSourceId.POCKETBOOK_DB -> setOf("com.obreey.reader")
    ProgressSourceId.LIBRERA_JSON -> setOf("com.foobnix.pdf.reader", "com.foobnix.pro.pdf.reader")
    ProgressSourceId.XREADER_CACHE -> setOf("com.xrz.xreaderV3")
}

/** Пакет FBReader: объявлен в пресете читалки, второй копии быть не должно. */
private const val FBREADER_PACKAGE = "com.fbreader"

/** Пакет CoolReader. */
private const val COOLREADER_PACKAGE = "org.coolreader"
