package com.bookshelf.app.ui.screens.opds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookshelf.app.data.model.OpdsCatalog

@Composable
fun AddCatalogDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить каталог") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = { onAdd(name, url) }
            ) {
                Text("Добавить")
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
fun CatalogsListDialog(
    catalogs: List<OpdsCatalog>,
    onDismiss: () -> Unit,
    onSelect: (OpdsCatalog) -> Unit,
    onDelete: (OpdsCatalog) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Каталоги") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(catalogs.size) { index ->
                    val catalog = catalogs[index]
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = catalog.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = catalog.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row {
                            TextButton(onClick = { onSelect(catalog) }) {
                                Text("Открыть")
                            }
                            
                            IconButton(onClick = { onDelete(catalog) }) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.Delete,
                                    contentDescription = "Удалить"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        modifier = modifier
    )
}
