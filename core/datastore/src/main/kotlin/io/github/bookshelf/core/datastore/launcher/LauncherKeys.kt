package io.github.bookshelf.core.datastore.launcher

/** Имена файлов хранилища лаунчера: одно место, чтобы боевой и тестовый код не расходились. */
object LauncherKeys {

    /** Файл DataStore состояния лаунчера (`filesDir/datastore/bookshelf_launcher.preferences_pb`). */
    const val LAUNCHER_FILE_NAME: String = "bookshelf_launcher"
}
