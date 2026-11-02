package io.github.bookshelf.core.update.install

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Правила установки APK (ADR-018, фаза 3).
 *
 * Проверяется то, что решается кодом, а не системой: безопасное имя файла и ворота разрешения
 * «установка неизвестных приложений». Сам установщик и FileProvider проверяются инструментально —
 * их поведение задаёт Android.
 */
class ApkInstallRulesTest {

    @Test
    fun asset_name_is_kept_when_already_safe() {
        assertThat(ApkInstallRules.fileName("BookShelf-2.0.2-full.apk")).isEqualTo("BookShelf-2.0.2-full.apk")
    }

    @Test
    fun slashes_and_quotes_are_replaced() {
        // Имя приходит из ответа GitHub: путь в нём — это запись вне каталога загрузок.
        assertThat(ApkInstallRules.fileName("../../evil\"name.apk")).doesNotContain("/")
        assertThat(ApkInstallRules.fileName("../../evil\"name.apk")).doesNotContain("\"")
    }

    @Test
    fun empty_name_falls_back_to_default() {
        assertThat(ApkInstallRules.fileName("")).isEqualTo("book-shelf-update.apk")
        assertThat(ApkInstallRules.fileName("   ")).isEqualTo("book-shelf-update.apk")
    }

    @Test
    fun permission_is_not_needed_before_android_8() {
        // До API 26 отдельного разрешения нет: установка спрашивает только системный диалог.
        assertThat(ApkInstallRules.installGate(canRequestPackageInstalls = false, sdkInt = 25))
            .isEqualTo(InstallGate.ALLOWED)
    }

    @Test
    fun permission_holders_proceed_to_installer() {
        assertThat(ApkInstallRules.installGate(canRequestPackageInstalls = true, sdkInt = 34))
            .isEqualTo(InstallGate.ALLOWED)
    }

    @Test
    fun missing_permission_opens_settings_request() {
        assertThat(ApkInstallRules.installGate(canRequestPackageInstalls = false, sdkInt = 34))
            .isEqualTo(InstallGate.REQUEST_PERMISSION)
    }
}
