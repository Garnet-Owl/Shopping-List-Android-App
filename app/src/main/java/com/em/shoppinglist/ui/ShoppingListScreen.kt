// File: app/src/main/java/com/em/shoppinglist/ui/ShoppingListScreen.kt

package com.em.shoppinglist.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.em.shoppinglist.viewmodel.ShoppingListViewModel
import com.em.shoppinglist.viewmodel.ShoppingListViewModel.SortOrder
import java.text.SimpleDateFormat
import java.util.*

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewListDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var listName by remember { mutableStateOf("") }
    val defaultName = "Shopping List ${
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
    }"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New List") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("List Name") },
                    placeholder = { Text(defaultName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(listName.ifBlank { defaultName })
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ShoppingListApp(viewModel: ShoppingListViewModel) {
    var showNewListDialog by remember { mutableStateOf(false) }
    var showLoadListDialog by remember { mutableStateOf(false) }
    var showShoppingList by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val savedLists by viewModel.savedLists.collectAsState()
    val currentSortOrder by viewModel.sortOrder.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    if (!showShoppingList) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top Bar with Title, Sort Button, and View Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shopping List Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view"
                        )
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Last Modified") },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.LAST_MODIFIED)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Creation Date") },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.CREATED_DATE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Total Amount") },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.TOTAL_AMOUNT)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showNewListDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New List")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New List")
                }

                Button(
                    onClick = { showLoadListDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Load List")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load List")
                }
            }

            // Shopping Lists or Empty State
            if (savedLists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No shopping lists yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedLists) { list ->
                            ShoppingListCard(
                                list = list,
                                onClick = {
                                    viewModel.loadList(list.id)
                                    showShoppingList = true
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedLists) { list ->
                            ShoppingListCard(
                                list = list,
                                onClick = {
                                    viewModel.loadList(list.id)
                                    showShoppingList = true
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        ShoppingListEditor(
            viewModel = viewModel,
            onBack = {
                viewModel.saveCurrentList()
                showShoppingList = false
            }
        )
    }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onConfirm = { name ->
                viewModel.createNewList(name)
                showNewListDialog = false
                showShoppingList = true
            }
        )
    }

    if (showLoadListDialog) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            uri?.let {
                viewModel.loadListFromUri(context, it)
                showLoadListDialog = false
                showShoppingList = true
            }
        }

        AlertDialog(
            onDismissRequest = { showLoadListDialog = false },
            title = { Text("Load List") },
            text = { Text("Select a shopping list to load") },
            confirmButton = {
                TextButton(
                    onClick = {
                        launcher.launch(arrayOf(
                            "text/plain",
                            "text/csv",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        ))
                    }
                ) {
                    Text("Choose File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoadListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListCard(
    list: ShoppingListViewModel.ShoppingListInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = { /* Add delete functionality */ }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete list",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ksh. ${list.totalAmount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatDate(list.lastModified),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListEditor(
    viewModel: ShoppingListViewModel,
    onBack: () -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var showExportMenu by remember { mutableStateOf(false) }
    val items by viewModel.shoppingItems.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("Shopping List") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export list"
                        )
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export as TXT") },
                            onClick = {
                                viewModel.exportList(context, ShoppingListViewModel.ExportFormat.TXT)
                                showExportMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as CSV") },
                            onClick = {
                                viewModel.exportList(context, ShoppingListViewModel.ExportFormat.CSV)
                                showExportMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Excel") },
                            onClick = {
                                viewModel.exportList(context, ShoppingListViewModel.ExportFormat.EXCEL)
                                showExportMenu = false
                            }
                        )
                    }
                }
            }
        )

        OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = { Text("Item Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Price (Ksh)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                if (itemName.isNotEmpty() && quantity.isNotEmpty() && price.isNotEmpty()) {
                    viewModel.addItem(
                        itemName,
                        quantity.toIntOrNull() ?: 0,
                        price.toDoubleOrNull() ?: 0.0
                    )
                    itemName = ""
                    quantity = ""
                    price = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text("Add Item")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Quantity: ${item.quantity}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Ksh. ${item.price}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total: Ksh. ${item.price * item.quantity}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeItem(item.id) }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Ksh. ${items.sumOf { it.price * it.quantity }}",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}