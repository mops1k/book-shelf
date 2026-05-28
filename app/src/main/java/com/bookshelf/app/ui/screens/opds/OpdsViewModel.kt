package com.bookshelf.app.ui.screens.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookshelf.app.data.model.OpdsCatalog
import com.bookshelf.app.data.model.OpdsEntry
import com.bookshelf.app.data.model.OpdsFeed
import com.bookshelf.app.data.remote.OpdsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class OpdsUiState(
    val catalogs: List<OpdsCatalog> = emptyList(),
    val selectedCatalogId: Long? = null,
    val currentFeed: OpdsFeed? = null,
    val currentUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadProgress: Float = 0f
)

class OpdsViewModel(private val opdsRepository: OpdsRepository) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OpdsUiState())
    val uiState: StateFlow<OpdsUiState> = _uiState.asStateFlow()
    
    init {
        loadCatalogs()
    }
    
    private fun loadCatalogs() {
        viewModelScope.launch {
            opdsRepository.getCatalogs()
                .catch { e -> 
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { catalogs ->
                    _uiState.update { it.copy(catalogs = catalogs) }
                    
                    // Auto-select first enabled catalog if none selected
                    if (_uiState.value.selectedCatalogId == null) {
                        catalogs.firstOrNull { it.isEnabled }?.let { catalog ->
                            selectCatalog(catalog)
                        }
                    }
                }
        }
    }
    
    fun selectCatalog(catalog: OpdsCatalog) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    selectedCatalogId = catalog.id,
                    isLoading = true,
                    error = null
                ) 
            }
            
            opdsRepository.fetchFeed(catalog.url)
                .onSuccess { feed ->
                    _uiState.update { 
                        it.copy(
                            currentFeed = feed,
                            currentUrl = catalog.url,
                            isLoading = false
                        ) 
                    }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            error = e.message ?: "Ошибка загрузки",
                            isLoading = false
                        ) 
                    }
                }
        }
    }
    
    fun navigate(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            opdsRepository.fetchFeed(url)
                .onSuccess { feed ->
                    _uiState.update { 
                        it.copy(
                            currentFeed = feed,
                            currentUrl = url,
                            isLoading = false
                        ) 
                    }
                }
                .onFailure { e ->
                    _uiState.update { 
                        it.copy(
                            error = e.message ?: "Ошибка навигации",
                            isLoading = false
                        ) 
                    }
                }
        }
    }
    
    fun loadNextPage() {
        val nextUrl = _uiState.value.currentFeed?.nextUrl ?: return
        navigate(nextUrl)
    }
    
    fun downloadBook(entry: OpdsEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val downloadLink = entry.downloadLinks.firstOrNull() 
                ?: entry.acquisitionLinks.firstOrNull()
            
            if (downloadLink != null) {
                val fileName = "${entry.title}.${getFileExtension(downloadLink.type)}"
                
                opdsRepository.downloadBook(downloadLink.href, fileName)
                    .onSuccess { file ->
                        // Book downloaded successfully
                        // In real app, add to library
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .onFailure { e ->
                        _uiState.update { 
                            it.copy(
                                error = e.message ?: "Ошибка скачивания",
                                isLoading = false
                            ) 
                        }
                    }
            } else {
                _uiState.update { 
                    it.copy(
                        error = "Нет ссылок для скачивания",
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    fun addCatalog(name: String, url: String) {
        viewModelScope.launch {
            val catalog = OpdsCatalog(
                name = name,
                url = url,
                isEnabled = true
            )
            opdsRepository.addCatalog(catalog)
        }
    }
    
    fun removeCatalog(catalog: OpdsCatalog) {
        viewModelScope.launch {
            opdsRepository.removeCatalog(catalog.id)
            
            if (_uiState.value.selectedCatalogId == catalog.id) {
                _uiState.update { 
                    it.copy(
                        selectedCatalogId = null,
                        currentFeed = null,
                        currentUrl = ""
                    ) 
                }
            }
        }
    }
    
    fun retry() {
        val currentUrl = _uiState.value.currentUrl
        if (currentUrl.isNotEmpty()) {
            navigate(currentUrl)
        } else {
            loadCatalogs()
        }
    }
    
    private fun getFileExtension(mimeType: String): String {
        return when {
            mimeType.contains("fb2") -> "fb2"
            mimeType.contains("epub") -> "epub"
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("txt") -> "txt"
            else -> "book"
        }
    }
}
