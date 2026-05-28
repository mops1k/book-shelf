package com.bookshelf.app.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookshelf.app.data.model.Book
import com.bookshelf.app.data.repository.BookRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val books: List<Book> = emptyList(),
    val authors: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val genres: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sortBy: String = "dateAdded",
    val isAscending: Boolean = false,
    val currentFilter: BookFilter? = null
)

data class BookFilter(
    val author: String? = null,
    val series: String? = null,
    val year: Int? = null,
    val genre: String? = null
)

class HomeViewModel(private val bookRepository: BookRepository) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadBooks()
        loadFilterOptions()
    }
    
    fun loadBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            bookRepository.allBooks
                .catch { e -> 
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { books ->
                    _uiState.update { 
                        it.copy(
                            books = sortBooks(books, it.sortBy, it.isAscending),
                            isLoading = false
                        ) 
                    }
                }
        }
    }
    
    fun searchBooks(query: String) {
        if (query.isBlank()) {
            loadBooks()
            return
        }
        
        viewModelScope.launch {
            bookRepository.searchBooks(query)
                .catch { e -> 
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { books ->
                    _uiState.update { it.copy(books = books) }
                }
        }
    }
    
    fun applyFilter(filter: BookFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentFilter = filter) }
            
            val booksFlow = when {
                filter.author != null -> bookRepository.getBooksByAuthor(filter.author)
                filter.series != null -> bookRepository.getBooksBySeries(filter.series)
                filter.year != null -> bookRepository.getBooksByYear(filter.year)
                filter.genre != null -> bookRepository.getBooksByGenre(filter.genre)
                else -> bookRepository.allBooks
            }
            
            booksFlow
                .catch { e -> 
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { books ->
                    _uiState.update { 
                        it.copy(
                            books = sortBooks(books, it.sortBy, it.isAscending)
                        ) 
                    }
                }
        }
    }
    
    fun setSort(sortBy: String, ascending: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(sortBy = sortBy, isAscending = ascending) }
            
            // Reload books with new sort
            val currentBooks = _uiState.value.books
            _uiState.update { 
                it.copy(
                    books = sortBooks(currentBooks, sortBy, ascending)
                ) 
            }
        }
    }
    
    private fun sortBooks(books: List<Book>, sortBy: String, ascending: Boolean): List<Book> {
        return when (sortBy) {
            "title" -> books.sortedWith(
                compareBy({ it.title.lowercase() })
            )
            "author" -> books.sortedWith(
                compareBy({ it.author?.lowercase() ?: "" })
            )
            "year" -> books.sortedWith(
                compareBy({ it.year ?: 0 })
            )
            "dateAdded" -> books.sortedWith(
                compareBy { it.dateAdded }
            )
            else -> books
        }.let {
            if (ascending) it else it.reversed()
        }
    }
    
    fun addBook(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = android.content.ContextWrapper(
                    androidx.compose.ui.platform.LocalContext.current as android.content.Context
                ).cacheDir
                
                // Get file from URI - this is simplified
                // In production, you'd need to copy the file properly
                val result = bookRepository.addBook(
                    java.io.File(uri.path ?: "")
                )
                
                result.onSuccess {
                    loadBooks()
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
            loadBooks()
        }
    }
    
    private fun loadFilterOptions() {
        viewModelScope.launch {
            launch {
                bookRepository.getAllAuthors().collect { authors ->
                    _uiState.update { it.copy(authors = authors) }
                }
            }
            launch {
                bookRepository.getAllSeries().collect { series ->
                    _uiState.update { it.copy(series = series) }
                }
            }
            launch {
                bookRepository.getAllYears().collect { years ->
                    _uiState.update { it.copy(years = years) }
                }
            }
            launch {
                bookRepository.getAllGenres().collect { genres ->
                    _uiState.update { it.copy(genres = genres) }
                }
            }
        }
    }
}
