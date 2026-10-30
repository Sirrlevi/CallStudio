@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.ui.call.ControlSpec
import com.callstudio.app.ui.call.InCallModel
import com.callstudio.app.ui.call.InCallScreen
import com.callstudio.app.ui.call.IncomingActions
import com.callstudio.app.ui.call.IncomingCallScreen
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.EmptyState
import com.callstudio.app.ui.components.LoadingBox
import com.callstudio.app.ui.viewmodels.PreviewViewModel
import kotlinx.coroutines.delay

@Composable
fun PreviewScreen(nav: NavController, callerId: Long, vm: PreviewViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val live by vm.liveCall.collectAsStateWithLifecycle()
    LaunchedEffect(callerId) { vm.loadCaller(callerId) }

    val s = settings
    val caller = vm.caller
    when {
        s == null || caller == null -> LoadingBox()
        live.isLive -> Scaffold(topBar = { AppTopBar("Preview", onBack = { nav.popBackStack() }) }) { pad ->
            Box(Modifier.padding(pad)) {
                EmptyState(Icons.Filled.PlayArrow, "A call is in progress", "Finish the current call, then preview again.")
            }
        }
        else -> {
            LaunchedEffect(Unit) { vm.startRinging() }
            DisposableEffect(Unit) { onDispose { vm.stopRinging() } }
            var start by remember(vm.inCall) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            var now by remember(vm.inCall) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(vm.inCall) {
                if (vm.inCall) {
                    start = SystemClock.elapsedRealtime()
                    while (true) {
                        now = SystemClock.elapsedRealtime()
                        delay(500)
                    }
                }
            }
            val leave = { vm.finish(); nav.popBackStack(); Unit }
            Box(Modifier.fillMaxSize()) {
                if (!vm.inCall) {
                    IncomingCallScreen(s.theme, caller, IncomingActions(onAnswer = { vm.answer() }, onDecline = leave))
                } else {
                    val controls = buildList {
                        add(ControlSpec("speaker", "Speaker", Icons.Filled.VolumeUp, vm.speakerOn, onClick = { vm.toggleSpeaker() }))
                    }
                    InCallScreen(s.theme, InCallModel(caller, (now - start).coerceAtLeast(0L), null, controls, onEnd = leave))
                }
                Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0x99000000)) {
                        Text("Preview · nothing is being called", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}
