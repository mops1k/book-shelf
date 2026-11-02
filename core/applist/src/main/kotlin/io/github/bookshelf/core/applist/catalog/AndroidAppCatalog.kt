package io.github.bookshelf.core.applist.catalog

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.ResolveInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.applist.model.AppCatalog
import io.github.bookshelf.core.applist.model.InstalledApp
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import javax.inject.Inject
import javax.inject.Singleton
import android.content.pm.LauncherActivityInfo
import android.os.Process
import kotlinx.coroutines.withContext

/**
 * Каталог приложений на `PackageManager` (ADR-015).
 *
 * Читает только активности с фильтром `MAIN`/`LAUNCHER` — то, что система показывает в своём
 * списке приложений. Видимость чужих пакетов на Android 11+ обеспечивает `<queries>` в
 * main-манифесте, а не `QUERY_ALL_PACKAGES`: play-flavor обходится без широкого разрешения.
 */
@Singleton
class AndroidAppCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : AppCatalog {

    override val ownPackage: String = context.packageName

    override suspend fun apps(): List<InstalledApp> = withContext(dispatchers.io) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(launcherIntent, PackageManager.GET_RESOLVED_FILTER)
            .mapNotNull { info -> info.toInstalledApp(packageManager) }
            // Один пакет может объявить несколько launcher-активностей; в списке приложение
            // должно быть одно — берём первую, как это делает системный лаунчер.
            .distinctBy { app -> app.packageName }
    }

    override fun launchIntent(app: InstalledApp): Intent? {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Явный компонент может оказаться отключённым после обновления приложения: тогда
        // запуск невозможен, и экран покажет баннер вместо падения.
        return intent.takeIf { resolveTarget(it) != null }
    }

    private fun resolveTarget(intent: Intent): ResolveInfo? = runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull()

    /** Подпись, версия и признак системного приложения; нечитаемое приложение пропускается. */
    private fun ResolveInfo.toInstalledApp(packageManager: PackageManager): InstalledApp? {
        val activity = activityInfo ?: return null
        val label = runCatching { activity.loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val applicationInfo = runCatching { activity.applicationInfo }.getOrNull() ?: return null
        return InstalledApp(
            packageName = activity.packageName,
            // Имя activity, а не класса Application: целью intent может быть только активность.
            activityName = activity.name,
            label = label,
            versionCode = versionCodeOf(packageManager, activity.packageName),
            isSystem = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }

    /**
     * Код версии приложения с совместимостью по API.
     *
     * `PackageInfo.longVersionCode` появился в API 28 (Android 9), а `minSdk` проекта — 24
     * (Android 7.0): прямой вызов уронил бы приложение на Android 7–8. Нашёл lint на гейте
     * 02.11.2026 — тесты этот случай не ловят, потому что android.jar возвращает значения-заглушки.
     */
    private fun versionCodeOf(packageManager: PackageManager, packageName: String): Long = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)
}
