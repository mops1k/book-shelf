package com.bookshelf.app.data.local

import androidx.room.*
import com.bookshelf.app.data.model.Book
import com.bookshelf.app.data.model.BookFormat
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    
    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    fun getAllBooks(): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): Book?
    
    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<Book>>
    
    @Query("SELECT DISTINCT author FROM books WHERE author IS NOT NULL ORDER BY author")
    fun getAllAuthors(): Flow<List<String>>
    
    @Query("SELECT DISTINCT seriesName FROM books WHERE seriesName IS NOT NULL ORDER BY seriesName")
    fun getAllSeries(): Flow<List<String>>
    
    @Query("SELECT DISTINCT year FROM books WHERE year IS NOT NULL ORDER BY year DESC")
    fun getAllYears(): Flow<List<Int>>
    
    @Query("SELECT DISTINCT genre FROM books WHERE genre IS NOT NULL ORDER BY genre")
    fun getAllGenres(): Flow<List<String>>
    
    @Query("SELECT * FROM books WHERE author = :author ORDER BY title")
    fun getBooksByAuthor(author: String): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE seriesName = :seriesName ORDER BY seriesNumber, title")
    fun getBooksBySeries(seriesName: String): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE year = :year ORDER BY title")
    fun getBooksByYear(year: Int): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE genre = :genre ORDER BY title")
    fun getBooksByGenre(genre: String): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE format = :format ORDER BY title")
    fun getBooksByFormat(format: BookFormat): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY title")
    fun getFavoriteBooks(): Flow<List<Book>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long
    
    @Update
    suspend fun updateBook(book: Book)
    
    @Delete
    suspend fun deleteBook(book: Book)
    
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)
    
    @Query("UPDATE books SET lastReadPosition = :position, readProgress = :progress WHERE id = :id")
    suspend fun updateReadProgress(id: Long, position: Long, progress: Float)
    
    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    
    @Query("SELECT COUNT(*) FROM books")
    fun getBooksCount(): Flow<Int>
}
