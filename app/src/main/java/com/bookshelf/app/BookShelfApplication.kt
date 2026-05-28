package com.bookshelf.app

import android.app.Application
import androidx.room.Room
import com.bookshelf.app.data.local.BookDatabase
import com.bookshelf.app.data.local.BookMetadataParser
import com.bookshelf.app.data.local.SettingsRepository
import com.bookshelf.app.data.remote.OpdsRepository
import com.bookshelf.app.data.repository.BookRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class BookShelfApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@BookShelfApplication)
            modules(appModule)
        }
    }
}

val appModule = module {
    // Database
    single {
        Room.databaseBuilder(
            androidContext(),
            BookDatabase::class.java,
            BookDatabase.DATABASE_NAME
        ).build()
    }
    
    single { get<BookDatabase>().bookDao() }
    
    // Repositories
    single { SettingsRepository(androidContext()) }
    single { BookMetadataParser(androidContext()) }
    single { BookRepository(get(), get(), androidContext()) }
    single { OpdsRepository(androidContext(), get()) }
    
    // ViewModels would be defined here but we'll use factory pattern for simplicity
}
