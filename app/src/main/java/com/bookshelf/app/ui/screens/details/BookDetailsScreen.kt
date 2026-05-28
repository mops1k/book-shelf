package com.bookshelf.app.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bookshelf.app.data.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    bookId: Long,
    onNavigateBack: () -> Unit,
    onOpenWithExternalReader: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    // In real app, get book from ViewModel
    val book = remember { 
        Book(
            id = bookId,
            title = "Пример книги",
            author = "Автор Примеров",
            seriesName = "Серия примеров",
            seriesNumber = 1.0,
            year = 2024,
            genre = "Фантастика",
            language = "ru",
            annotation = "Это пример аннотации к книге. Здесь должно быть описание сюжета и другая полезная информация о произведении.",
            filePath = "/path/to/book.fb2",
            fileSize = 1024 * 1024, // 1 MB
            format = com.bookshelf.app.data.model.BookFormat.FB2
        )
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация о книге") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cover and basic info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cover image
                Box(
                    modifier = Modifier
                        .size(120.dp, 180.dp)
                        .materialBorder(),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverImageBytes != null && book.coverImageBytes.isNotEmpty()) {
                        Image(
                            bitmap = book.coverImageBytes.asImageBitmap(),
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = book.format.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                // Basic info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    book.author?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    book.seriesName?.let { series ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = series,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            book.seriesNumber?.let { number ->
                                Text(
                                    text = " #$number",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        AssistChip(
                            onClick = { },
                            label = { Text(book.format.name) },
                            leadingIcon = {
                                Icon(
                                    when (book.format) {
                                        com.bookshelf.app.data.model.BookFormat.FB2 -> Icons.Default.Description
                                        com.bookshelf.app.data.model.BookFormat.EPUB -> Icons.Default.MenuBook
                                        com.bookshelf.app.data.model.BookFormat.PDF -> Icons.Default.PictureAsPdf
                                        else -> Icons.Default.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        
                        book.year?.let { year ->
                            AssistChip(
                                onClick = { },
                                label = { Text(year.toString()) }
                            )
                        }
                        
                        book.language?.let { lang ->
                            AssistChip(
                                onClick = { },
                                label = { Text(lang.uppercase()) }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // Metadata
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Метаданные",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    book.genre?.let { genre ->
                        MetadataRow(label = "Жанр", value = genre)
                    }
                    
                    book.language?.let { lang ->
                        MetadataRow(label = "Язык", value = lang)
                    }
                    
                    MetadataRow(
                        label = "Размер файла",
                        value = formatFileSize(book.fileSize)
                    )
                    
                    MetadataRow(
                        label = "Дата добавления",
                        value = formatTimestamp(book.dateAdded)
                    )
                    
                    MetadataRow(
                        label = "Путь к файлу",
                        value = book.filePath
                    )
                }
            }
            
            // Annotation
            book.annotation?.let { annotation ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Аннотация",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = annotation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenWithExternalReader(book) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть")
                }
                
                OutlinedButton(
                    onClick = { /* Share */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Поделиться")
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Подтвердите удаление") },
            text = { Text("Вы уверены, что хотите удалить книгу \"${book.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Delete book
                        showDeleteDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}

private fun Modifier.materialBorder(): Modifier {
    return this
}
