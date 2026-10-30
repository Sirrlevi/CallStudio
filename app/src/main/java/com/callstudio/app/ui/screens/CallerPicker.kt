package com.callstudio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.data.displayNumber
import com.callstudio.app.ui.components.CallerAvatar

@Composable
fun CallerPickDialog(
    callers: List<CallerEntity>,
    onPick: (CallerEntity) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a caller") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(callers, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = if (c.phone.isNotBlank()) ({ Text(c.displayNumber()) }) else null,
                        leadingContent = { CallerAvatar(c.photoFile, c.name, 40.dp) },
                        modifier = Modifier.clickable { onPick(c) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("New caller") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
