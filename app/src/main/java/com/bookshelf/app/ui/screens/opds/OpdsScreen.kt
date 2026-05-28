package com.bookshelf.app.ui.screens.opds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookshelf.app.data.model.OpdsCatalog
import com.bookshelf.app.data.model.OpdsEntry
import com.bookshelf.app.data.model.OpdsNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    viewModel: OpdsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showAddCatalogDialog by remember { mutableStateOf(false) }
    var showCatalogsList by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Онлайн библиотека") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showCatalogsList = true }) {
                        Icon(Icons.Default.List, contentDescription = "Каталоги")
                    }
                    IconButton(onClick = { showAddCatalogDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить каталог")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error ?: "Ошибка",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("Повторить")
                        }
                    }
                }
                
                uiState.currentFeed != null -> {
                    val feed = uiState.currentFeed!!
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = feed.title,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    
                                    if (uiState.currentUrl.isNotEmpty()) {
                                        Text(
                                            text = uiState.currentUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Navigation links
                        if (feed.navigation.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Разделы",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            
                            items(feed.navigation) { nav ->
                                NavigationCard(
                                    navigation = nav,
                                    onClick = { viewModel.navigate(nav.href) }
                                )
                            }
                        }
                        
                        // Book entries
                        if (feed.entries.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Книги",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            
                            items(feed.entries) { entry ->
                                BookEntryCard(
                                    entry = entry,
                                    onDownload = { viewModel.downloadBook(entry) }
                                )
                            }
                        }
                        
                        // Next page
                        if (feed.nextUrl != null) {
                            item {
                                Button(
                                    onClick = { viewModel.loadNextPage() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Следующая страница")
                                }
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                
                else -> {
                    // Show catalog list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.catalogs) { catalog ->
                            CatalogCard(
                                catalog = catalog,
                                isSelected = uiState.selectedCatalogId == catalog.id,
                                onClick = { viewModel.selectCatalog(catalog) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showAddCatalogDialog) {
        AddCatalogDialog(
            onDismiss = { showAddCatalogDialog = false },
            onAdd = { name, url ->
                viewModel.addCatalog(name, url)
                showAddCatalogDialog = false
            }
        )
    }
    
    if (showCatalogsList) {
        CatalogsListDialog(
            catalogs = uiState.catalogs,
            onDismiss = { showCatalogsList = false },
            onSelect = { catalog ->
                viewModel.selectCatalog(catalog)
                showCatalogsList = false
            },
            onDelete = { catalog ->
                viewModel.removeCatalog(catalog)
            }
        )
    }
}

@Composable
fun CatalogCard(
    catalog: OpdsCatalog,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = catalog.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = catalog.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!catalog.isEnabled) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "Отключен",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NavigationCard(
    navigation: OpdsNavigation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = navigation.title,
                style = MaterialTheme.typography.bodyLarge
            )
            
            navigation.count?.let { count ->
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun BookEntryCard(
    entry: OpdsEntry,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium
            )
            
            entry.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            entry.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Скачать")
                }
            }
        }
    }
}
