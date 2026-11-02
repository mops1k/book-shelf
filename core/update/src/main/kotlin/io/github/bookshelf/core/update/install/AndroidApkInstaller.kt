package io.github.bookshelf.core.update.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Установка APK через системный установщик (ADR-018).
 *
 * Своей установки нет и быть не может: Android показывает диалог подтверждения, и приложение
 * с обычными правами обойти его не может. Наша задача — отдать файл через `content://`
 * (FileProvider) и, если нужно, довести пользователя до разрешения «установка неизвестных
 * приложений».
 */
@Singleton
class AndroidApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApkInstaller {

    override fun gate(): InstallGate = ApkInstallRules.installGate(
        canRequestPackageInstalls = canRequestPackageInstalls(),
        sdkInt = Build.VERSION.SDK_INT,
    )

    override fun requestPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun install(file: File) {
        // FileProvider отдаёт content:// — обычный file:// на Android 7+ даёт FileUriExposedException.
        val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= ApkInstallRules.ANDROID_8) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }
}
