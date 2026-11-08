@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.contacts.PickPhoneContract
import com.callstudio.app.data.displayNumber
import com.callstudio.app.domain.Countries
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.components.ConfirmDialog
import com.callstudio.app.ui.components.EmptyState
import com.callstudio.app.ui.components.LoadingBox
import com.callstudio.app.ui.viewmodels.CallersViewModel
import kotlinx.coroutines.launch

@Composable
fun CallersScreen(nav: NavController, vm: CallersViewModel = viewModel()) {
    val callers by vm.callers.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<CallerEntity?>(null) }
    val list = callers

    Scaffold(
        topBar = { AppTopBar("Callers") },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.callerEdit(0)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add caller")
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(
                    Icons.Filled.Person, "No callers yet",
                    "Add someone from your contacts or create a custom caller with their own photo.",
                    "Add caller",
                ) { nav.navigate(Routes.callerEdit(0)) }
                else -> LazyColumn {
                    items(list, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = if (c.phone.isNotBlank()) ({ Text(c.displayNumber()) }) else null,
                            leadingContent = { CallerAvatar(c.photoFile, c.name, 44.dp) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { nav.navigate(Routes.schedule(c.id)) }) {
                                        Icon(Icons.Filled.Alarm, contentDescription = "Schedule a call from ${c.name}")
                                    }
                                    IconButton(onClick = { deleting = c }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${c.name}")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { nav.navigate(Routes.callerEdit(c.id)) },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { c ->
        ConfirmDialog(
            title = "Delete ${c.name}?",
            text = "Calls already scheduled for this caller will be cancelled. Past calls stay in your history.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(c); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun CallerEditScreen(nav: NavController, callerId: Long, vm: CallersViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(callerId <= 0) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var iso by rememberSaveable { mutableStateOf(Countries.defaultIso()) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var originalPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showCountries by remember { mutableStateOf(false) }

    LaunchedEffect(callerId) {
        if (!loaded) {
            vm.load(callerId)?.let {
                name = it.name
                iso = it.countryIso.ifBlank { Countries.defaultIso() }
                phone = PhoneNumbers.group(it.phone, iso)
                photo = it.photoFile
                originalPhoto = it.photoFile
            }
            loaded = true
        }
    }

    fun replacePhoto(newName: String?) {
        if (photo != null && photo != originalPhoto) vm.discardPhoto(photo)
        photo = newName
    }

    fun leave() {
        if (photo != originalPhoto) vm.discardPhoto(photo)
        nav.popBackStack()
    }
    BackHandler { leave() }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val imported = vm.importPhoto(uri)
            busy = false
            if (imported == null) error = "That image couldn't be read." else { error = null; replacePhoto(imported) }
        }
    }
    val contactPicker = rememberLauncherForActivityResult(PickPhoneContract()) { uri ->
        if (uri != null) scope.launch {
            val picked = vm.readContact(uri)
            if (picked == null) {
                error = "That contact couldn't be read."
            } else {
                error = null
                if (picked.info.name.isNotBlank()) name = picked.info.name
                val parsed = PhoneNumbers.normalize(picked.info.number, iso)
                iso = parsed.iso
                phone = PhoneNumbers.group(parsed.national, parsed.iso)
                notice = when {
                    picked.photoFile != null -> null
                    !SystemAccess.contactsGranted(ctx) ->
                        "Contacts access is off, so the contact's photo couldn't be copied. Allow it in app settings, or choose a photo yourself."
                    photo == null -> "This contact has no photo saved."
                    else -> null
                }
                if (picked.photoFile != null) replacePhoto(picked.photoFile)
            }
        }
    }
    // Asked only here, when you choose from contacts. Either answer still opens the picker.
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactPicker.launch(Unit)
    }

    fun save(thenPreview: Boolean) {
        if (name.isBlank() && phone.isBlank()) {
            error = "Enter a name or a phone number."
            return
        }
        scope.launch {
            busy = true
            val parsed = PhoneNumbers.normalize(phone, iso)
            val id = vm.save(callerId.coerceAtLeast(0), name, parsed.national, parsed.iso, photo)
            busy = false
            if (thenPreview) {
                nav.navigate(Routes.preview(id)) { popUpTo(Routes.CALLER_EDIT) { inclusive = true } }
            } else nav.popBackStack()
        }
    }

    val parsedNow = PhoneNumbers.normalize(phone, iso)
    val shownNumber = PhoneNumbers.display(parsedNow.iso, parsedNow.national)

    Scaffold(topBar = { AppTopBar(if (callerId > 0) "Edit caller" else "New caller", onBack = { leave() }) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallerAvatar(photo, name.ifBlank { phone }, 112.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (photo == null) "Choose photo" else "Change photo")
                }
                if (photo != null) TextButton(onClick = { replacePhoto(null) }) { Text("Remove") }
            }
            OutlinedButton(
                onClick = {
                    if (SystemAccess.contactsGranted(ctx)) contactPicker.launch(Unit)
                    else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Contacts, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose from contacts")
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showCountries = true }, modifier = Modifier.height(56.dp)) {
                    Text("${Countries.flag(iso)} ${Countries.dialLabelOf(iso)}")
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { v -> phone = v.filter { it.isDigit() || it == ' ' || it == '+' || it == '-' }.take(30) },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Country: ${Countries.nameOf(iso) ?: "Not set"}. Typing + and a code (for example +91) switches the country.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CallerAvatar(photo, name.ifBlank { phone }, 48.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(name.ifBlank { shownNumber.ifBlank { "Caller" } }, style = MaterialTheme.typography.titleMedium)
                        Text("Mobile · ${shownNumber.ifBlank { "no number" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { save(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            OutlinedButton(onClick = { save(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save and preview call")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCountries) {
        CountryPickerDialog(
            onPick = { iso = it.iso; showCountries = false },
            onDismiss = { showCountries = false },
        )
    }
}
