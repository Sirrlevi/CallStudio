package com.callstudio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callstudio.app.domain.Countries
import com.callstudio.app.domain.Country

/** Searchable country list; search by name, ISO code, or calling code (for example "91"). */
@Composable
fun CountryPickerDialog(onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val countries = remember { Countries.all.map { it to it.name }.sortedBy { it.second } }
    val shown = remember(query) {
        val q = query.trim().removePrefix("+")
        if (q.isEmpty()) countries
        else countries.filter { (c, name) ->
            name.contains(q, ignoreCase = true) || c.iso.equals(q, ignoreCase = true) || c.dial.startsWith(q)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Country code") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(30) }, label = { Text("Search") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown, key = { it.first.iso }) { (country, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = { Text(country.flag, fontSize = 22.sp) },
                            trailingContent = { Text(country.dialLabel) },
                            modifier = Modifier.clickable { onPick(country) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
