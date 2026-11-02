package io.github.bookshelf.core.update.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.update.install.ApkInstallRules
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Скачивание APK обновления
 *
 * Почему `DownloadManager`, а не свой HTTP-клиент: система докачивает после обрыва, показывает
 * прогресс в уведомлении и продолжает работу, когда приложение закрыто. Файл кладётся в
 * `Android/data/<пакет>/files/Download/updates` — этот каталог отдаётся установщику через
 * `FileProvider`.
 */
@Singleton
class AndroidUpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdateDownloader {

    override fun download(url: String, assetName: String): Flow<UpdateDownloadState> = flow {
        val manager = context.getSystemService<DownloadManager>()
            ?: return@flow emit(UpdateDownloadState.Failed(REASON_NO_MANAGER))
        val fileName = ApkInstallRules.fileName(assetName)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, UPDATE_DIR + fileName)
        val id = try {
            manager.enqueue(request)
        } catch (error: IllegalArgumentException) {
            // Некорректная ссылка в релизе: показываем причину, а не падаем.
            return@flow emit(UpdateDownloadState.Failed(error.message.orEmpty()))
        }
        emit(UpdateDownloadState.Running(percent = null))
        while (true) {
            when (val snapshot = query(manager, id)) {
                null -> return@flow emit(UpdateDownloadState.Failed(REASON_LOST))
                else -> when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_DIR + fileName)
                        val done = if (file.exists() && file.length() > 0) {
                            UpdateDownloadState.Done(file)
                        } else {
                            UpdateDownloadState.Failed(REASON_EMPTY)
                        }
                        return@flow emit(done)
                    }
                    DownloadManager.STATUS_FAILED -> return@flow emit(UpdateDownloadState.Failed(REASON_FAILED))
                    else -> emit(UpdateDownloadState.Running(snapshot.percent()))
                }
            }
            delay(POLL_MS)
        }
    }.flowOn(Dispatchers.IO)

    private class Snapshot(val status: Int, val downloaded: Long, val total: Long) {
        /** Прогресс 0…100; `null`, если система ещё не знает размер. */
        fun percent(): Int? = if (total > 0) (downloaded * PERCENT_MAX / total).toInt() else null
    }

    private fun query(manager: DownloadManager, id: Long): Snapshot? {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return Snapshot(status, downloaded, total)
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val UPDATE_DIR = "updates/"
        const val POLL_MS = 500L
        const val PERCENT_MAX = 100
        const val REASON_NO_MANAGER = "нет DownloadManager"
        const val REASON_LOST = "загрузка не найдена"
        const val REASON_EMPTY = "файл не скачался"
        const val REASON_FAILED = "загрузка прервана"
    }
}
