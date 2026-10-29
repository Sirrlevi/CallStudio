package com.callstudio.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.container
import com.callstudio.app.contacts.ContactInfo
import com.callstudio.app.contacts.ContactsHelper
import com.callstudio.app.data.CallerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallersViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** Null while loading. */
    val callers: StateFlow<List<CallerEntity>?> =
        c.callers.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun load(id: Long): CallerEntity? = c.callers.get(id)

    suspend fun save(id: Long, name: String, phone: String, photoFile: String?): Long =
        c.callers.save(id, name, phone, photoFile)

    fun delete(caller: CallerEntity) {
        viewModelScope.launch { c.callers.delete(caller) }
    }

    suspend fun importPhoto(uri: Uri): String? = c.photos.import(uri)

    fun discardPhoto(name: String?) = c.photos.delete(name)

    /** Reads the picked contact row (no contacts permission needed) and copies its photo if one is readable. */
    suspend fun readContact(uri: Uri): Pair<ContactInfo, String?>? {
        val info = withContext(Dispatchers.IO) { ContactsHelper.read(getApplication(), uri) } ?: return null
        val photo = info.photoUri?.let { c.photos.import(it) }
        return info to photo
    }
}
