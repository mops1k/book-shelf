package io.github.bookshelf.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.dao.BookCopyDao
import io.github.bookshelf.core.database.migration.ALL_MIGRATIONS
import javax.inject.Singleton

/**
 * DI-граф модуля БД: единственный экземпляр [BookshelfDatabase] на приложение
 * и все DAO из него.
 *
 * `fallbackToDestructiveMigration()` здесь сознательно **не** вызывается: пользовательская
 * библиотека не должна теряться при обновлении («Архитектура» §3.4). Вместо этого
 * подключается массив [ALL_MIGRATIONS].
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Открыть БД приложения.
     *
     * Контекст приложения, а не Activity: база живёт дольше любого экрана.
     */
    // addMigrations принимает vararg — без spread-оператора массив не передать.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideBookshelfDatabase(@ApplicationContext context: Context): BookshelfDatabase =
        Room.databaseBuilder(context, BookshelfDatabase::class.java, BookshelfDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    /**
     * Связи «файл-копия → книга».
     *
     * Живут в модуле базы, а не в `DaoModule`: там ровно двадцать провайдеров при пороге detekt
     * двадцать, и двадцать первый упирается в порог. Здесь же рядом с самой базой.
     */
    @Provides
    fun provideBookCopyDao(database: BookshelfDatabase): BookCopyDao = database.bookCopyDao()
}
