package com.callstudio.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppIo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
