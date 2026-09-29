package io.github.bookshelf.di

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.common.app.FolderOpener
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.storage.saf.parentFolderUri
import javax.inject.Singleton

/**
 * «Показать в папке»: открыть системный файловый менеджер на папке книги.
 *
 * Открывается именно папка, а не книга: файловый менеджер умеет открывать папки по адресу SAF
 * (`ACTION_VIEW` + тип «папка»), и это единственный способ показать человеку место файла — путь
 * на диске у SAF-документа не существует вовсе.
 *
 * Если адрес папки не вычислить или в системе нет обработчика, возвращается понятная причина:
 * нажатие кнопки не должно заканчиваться ничем.
 */
@Module
@InstallIn(SingletonComponent::class)
object FolderModule {

    @Provides
    @Singleton
    fun provideFolderOpener(@ApplicationContext context: Context): FolderOpener = FolderOpener { documentUri ->
        val folder = parentFolderUri(Uri.parse(documentUri))
            ?: return@FolderOpener AppResult.failure(
                AppError.Unsupported(
                    what = "folder",
                    message = "Не удалось определить папку книги: адрес не похож на документ SAF",
                ),
            )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            AppResult.success(Unit)
        } catch (_: ActivityNotFoundException) {
            AppResult.failure(
                AppError.Unsupported(
                    what = "folder",
                    message = "В системе нет программы, которая открывает папки",
                ),
            )
        }
    }
}
