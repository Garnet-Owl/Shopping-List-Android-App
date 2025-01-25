package com.em.shoppinglist.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun LoadListDialog(
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load List") },
        text = { Text("Select a shopping list file to load") },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}