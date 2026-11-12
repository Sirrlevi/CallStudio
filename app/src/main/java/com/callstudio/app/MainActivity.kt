package com.callstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.callstudio.app.ui.AppNav
import com.callstudio.app.ui.theme.CallStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallStudioTheme {
                Box(Modifier.fillMaxSize()) { AppNav() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Looks for a new build when the app opens or comes back to the front. The Dashboard shows the result
        // with a Download button; the update itself is handled by AppContainer.updates.
        container.updates.onAppResumed()
    }
}
