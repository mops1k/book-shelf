package com.bookshelf.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bookshelf.app.data.local.BookDao
import com.bookshelf.app.data.local.BookMetadataParser
import com.bookshelf.app.data.model.Book
import com.bookshelf.app.data.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BookRepository(
    private val bookDao: BookDao,
    private val metadataParser: BookMetadataParser,
    private val context: Context
) {
    
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    
    fun searchBooks(query: String): Flow<List<Book>> = bookDao.searchBooks(query)
    
    fun getBooksByAuthor(author: String): Flow<List<Book>> = bookDao.getBooksByAuthor(author)
    
    fun getBooksBySeries(seriesName: String): Flow<List<Book>> = bookDao.getBooksBySeries(seriesName)
    
    fun getBooksByYear(year: Int): Flow<List<Book>> = bookDao.getBooksByYear(year)
    
    fun getBooksByGenre(genre: String): Flow<List<Book>> = bookDao.getBooksByGenre(genre)
    
    fun getFavoriteBooks(): Flow<List<Book>> = bookDao.getFavoriteBooks()
    
    fun getAllAuthors(): Flow<List<String>> = bookDao.getAllAuthors()
    
    fun getAllSeries(): Flow<List<String>> = bookDao.getAllSeries()
    
    fun getAllYears(): Flow<List<Int>> = bookDao.getAllYears()
    
    fun getAllGenres(): Flow<List<String>> = bookDao.getAllGenres()
    
    suspend fun getBookById(id: Long): Book? = bookDao.getBookById(id)
    
    suspend fun addBook(file: File): Result<Book> = withContext(Dispatchers.IO) {
        try {
            // Copy file to app storage if needed
            val destFile = copyToAppStorage(file)
            
            // Parse metadata
            val book = metadataParser.parseMetadata(destFile)
            
            // Save to database
            val id = bookDao.insertBook(book)
            val savedBook = book.copy(id = id)
            
            Result.success(savedBook)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun copyToAppStorage(sourceFile: File): File {
        val booksDir = File(context.filesDir, "books")
        booksDir.mkdirs()
        
        val destFile = File(booksDir, sourceFile.name)
        
        if (!destFile.exists()) {
            FileOutputStream(destFile).use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        
        return destFile
    }
    
    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        // Delete from database
        bookDao.deleteBook(book)
        
        // Delete file
        val file = File(book.filePath)
        if (file.exists()) {
            file.delete()
        }
    }
    
    suspend fun deleteBookById(id: Long) = withContext(Dispatchers.IO) {
        bookDao.deleteBookById(id)
    }
    
    suspend fun updateReadProgress(id: Long, position: Long, progress: Float) {
        bookDao.updateReadProgress(id, position, progress)
    }
    
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        bookDao.toggleFavorite(id, isFavorite)
    }
    
    fun openBookWithExternalReader(book: Book): Intent? {
        val file = File(book.filePath)
        if (!file.exists()) return null
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val mimeType = when (book.format) {
            BookFormat.FB2 -> "application/x-fictionbook+xml"
            BookFormat.EPUB -> "application/epub+zip"
            BookFormat.PDF -> "application/pdf"
            BookFormat.UNKNOWN -> "*/*"
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            null
        }
    }
    
    fun getShareIntent(book: Book): Intent? {
        val file = File(book.filePath)
        if (!file.exists()) return null
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        return Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
