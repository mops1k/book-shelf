package com.bookshelf.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FilterDialog(
    authors: List<String>,
    series: List<String>,
    years: List<Int>,
    genres: List<String>,
    onDismiss: () -> Unit,
    onApplyFilter: (BookFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAuthor by remember { mutableStateOf<String?>(null) }
    var selectedSeries by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Автор", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (authors.isEmpty()) {
                        Text("Нет авторов", style = MaterialTheme.typography.bodySmall)
                    } else {
                        authors.forEach { author ->
                            FilterChip(
                                label = author,
                                selected = selectedAuthor == author,
                                onClick = { 
                                    selectedAuthor = if (selectedAuthor == author) null else author 
                                }
                            )
                        }
                    }
                }
                
                item {
                    Text("Серия", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (series.isEmpty()) {
                        Text("Нет серий", style = MaterialTheme.typography.bodySmall)
                    } else {
                        series.forEach { s ->
                            FilterChip(
                                label = s,
                                selected = selectedSeries == s,
                                onClick = { 
                                    selectedSeries = if (selectedSeries == s) null else s 
                                }
                            )
                        }
                    }
                }
                
                item {
                    Text("Год", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (years.isEmpty()) {
                        Text("Нет годов", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            years.forEach { year ->
                                FilterChip(
                                    label = year.toString(),
                                    selected = selectedYear == year,
                                    onClick = { 
                                        selectedYear = if (selectedYear == year) null else year 
                                    }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Text("Жанр", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (genres.isEmpty()) {
                        Text("Нет жанров", style = MaterialTheme.typography.bodySmall)
                    } else {
                        genres.forEach { genre ->
                            FilterChip(
                                label = genre,
                                selected = selectedGenre == genre,
                                onClick = { 
                                    selectedGenre = if (selectedGenre == genre) null else genre 
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApplyFilter(
                    BookFilter(
                        author = selectedAuthor,
                        series = selectedSeries,
                        year = selectedYear,
                        genre = selectedGenre
                    )
                )
            }) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        modifier = modifier
    )
}

@Composable
fun SortDialog(
    currentSortBy: String,
    isAscending: Boolean,
    onDismiss: () -> Unit,
    onApplySort: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortBy by remember { mutableStateOf(currentSortBy) }
    var ascending by remember { mutableStateOf(isAscending) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сортировка") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("Сортировать по:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    listOf(
                        "dateAdded" to "Дате добавления",
                        "title" to "Названию",
                        "author" to "Автору",
                        "year" to "Году"
                    ).forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label)
                            RadioButton(
                                selected = sortBy == key,
                                onClick = { sortBy = key }
                            )
                        }
                    }
                }
                
                HorizontalDivider()
                
                Column {
                    Text("Порядок:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("По возрастанию")
                        RadioButton(
                            selected = ascending,
                            onClick = { ascending = true }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("По убыванию")
                        RadioButton(
                            selected = !ascending,
                            onClick = { ascending = false }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApplySort(sortBy, ascending)
            }) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        modifier = modifier
    )
}
