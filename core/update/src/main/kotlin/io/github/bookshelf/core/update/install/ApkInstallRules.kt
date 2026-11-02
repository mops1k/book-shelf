package io.github.bookshelf.core.update.install

/**
 * Правила установки APK: имя файла и ворота разрешения (ADR-018).
 *
 * Вынесено из Android-кода, чтобы проверялось юнит-тестами: имя файла приходит из ответа GitHub,
 * и путь в нём — это запись вне каталога загрузок, а не «просто строка».
 */
object ApkInstallRules {

    /** API 26 — Android 8.0, где появилось разрешение на установку из неизвестных источников. */
    const val ANDROID_8: Int = 26

    private const val FALLBACK_NAME = "book-shelf-update.apk"

    /** Безопасное имя APK: без каталогов и кавычек, с запасным именем для пустой строки. */
    fun fileName(raw: String): String {
        val cleaned = raw.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('"', '_')
        return cleaned.ifBlank { FALLBACK_NAME }
    }

    /** Нужно ли сначала запросить разрешение на установку. */
    fun installGate(canRequestPackageInstalls: Boolean, sdkInt: Int): InstallGate = when {
        // До Android 8 разрешение «неизвестные приложения» выдавалось флажком в настройках
        // безопасности и проверять его из приложения не нужно.
        sdkInt < ANDROID_8 -> InstallGate.ALLOWED
        canRequestPackageInstalls -> InstallGate.ALLOWED
        else -> InstallGate.REQUEST_PERMISSION
    }
}
