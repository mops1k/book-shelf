package com.bookshelf.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bookshelf.app.data.model.Book

@Database(entities = [Book::class], version = 1, exportSchema = false)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    
    companion object {
        const val DATABASE_NAME = "bookshelf_db"
    }
}
