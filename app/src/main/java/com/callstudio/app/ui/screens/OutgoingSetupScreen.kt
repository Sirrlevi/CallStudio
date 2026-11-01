@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.outgoing.OutgoingSettings
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.viewmodels.OutgoingViewModel
import kotlinx.coroutines.launch

@Composable
fun OutgoingSetupScreen(nav: NavController, vm: OutgoingViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.settings.collectAsStateWithLifecycle()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var sid by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var mine by rememberSaveable { mutableStateOf("") }
    var cloud by rememberSaveable { mutableStateOf("") }
    var preset by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(saved) {
        val s = saved
        if (!loaded && s != null) {
            sid = s.accountSid; token = s.authToken; from = s.fromNumber
            mine = s.myNumber; cloud = s.cloudName; preset = s.uploadPreset
            loaded = true
        }
    }

    Scaffold(topBar = { AppTopBar("Outgoing setup", onBack = { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Twilio (places the call)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Create a Twilio account, buy or get a voice-capable number, and copy the Account SID and Auth Token from its " +
                    "console. Trial accounts can only call numbers you have verified there.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(sid, { sid = it.trim() }, label = { Text("Account SID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                token, { token = it.trim() }, label = { Text("Auth Token") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                from, { from = it.filter { c -> c.isDigit() || c == '+' } }, label = { Text("Your Twilio number, like +14155550123") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                mine, { mine = it.filter { c -> c.isDigit() || c == '+' } }, label = { Text("Your own phone (to join calls live)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )

            Text("Clip upload (for audio files from this phone)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Twilio plays audio from a web link. In a free Cloudinary account, add an unsigned upload preset, then enter the " +
                    "cloud name and preset name here. Uploaded clips can be opened by anyone with the link. Skip this if you only " +
                    "add clips as links.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(cloud, { cloud = it.trim() }, label = { Text("Cloudinary cloud name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(preset, { preset = it.trim() }, label = { Text("Unsigned upload preset") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(
                "Your Auth Token is stored encrypted on this phone and is sent only to Twilio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    when {
                        sid.isBlank() || token.isBlank() -> error = "Enter the Account SID and Auth Token."
                        !PhoneNumbers.isE164(from) -> error = "Your Twilio number must look like +14155550123."
                        mine.isNotBlank() && !PhoneNumbers.isE164(mine) -> error = "Your own phone must start with + and the country code."
                        (cloud.isBlank()) != (preset.isBlank()) -> error = "Enter both the cloud name and the upload preset, or neither."
                        else -> scope.launch {
                            vm.saveSettings(OutgoingSettings(sid, token, from, mine, cloud, preset))
                            error = null
                            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                            nav.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Row {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            vm.clearSettings()
                            sid = ""; token = ""; from = ""; mine = ""; cloud = ""; preset = ""
                            Toast.makeText(ctx, "Removed from this phone", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove all details from this phone") }
            }
        }
    }
}
