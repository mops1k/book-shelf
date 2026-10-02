package io.github.bookshelf.core.storage.provider

import android.net.Uri
import java.io.File

/**
 * Хелпер `FileProvider` для передачи книг внешним читалкам.
 *
 * Часть читалок не принимает `content://` от SAF-провайдера и требует «обычный» URI, которому
 * можно выдать грант; в этом случае книга отдаётся через наш `FileProvider`
 * («Архитектура» §7, «Читалки»).
 *
 * Провайдер объявляется в манифесте `:app` с authority `"$applicationId.fileprovider"`
 * и `file_paths.xml`, покрывающим `cacheDir/export`. Файл, для которого строится URI,
 * обязан лежать в разрешённом корне.
 */
object ExportFileProvider {

    private const val CONTENT_SCHEME = "content"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /** Authority провайдера по `applicationId`; должно совпадать с манифестом и `file_paths.xml`. */
    fun authority(applicationId: String): String = applicationId + AUTHORITY_SUFFIX

    /**
     * `content://`-URI файла для передачи читалке.
     *
     * В URI попадает только имя файла: соответствие «имя → реальный каталог» задаётся
     * `file_paths.xml`, а вызывающий код обязан передавать файл из разрешённого корня
     * (`cacheDir/export`), иначе провайдер бросит `IllegalArgumentException`.
     */
    fun uriFor(applicationId: String, file: File): String =
        Uri.Builder()
            .scheme(CONTENT_SCHEME)
            .authority(authority(applicationId))
            .appendPath(file.name)
            .build()
            .toString()
}
