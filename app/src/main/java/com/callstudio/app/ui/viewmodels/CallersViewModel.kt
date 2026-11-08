package com.callstudio.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.container
import com.callstudio.app.contacts.ContactInfo
import com.callstudio.app.contacts.ContactsHelper
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.domain.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallersViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** Null while loading. */
    val callers: StateFlow<List<CallerEntity>?> =
        c.callers.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun load(id: Long): CallerEntity? = c.callers.get(id)

    suspend fun save(id: Long, name: String, phone: String, countryIso: String, photoFile: String?): Long =
        c.callers.save(id, name, phone, countryIso, photoFile)

    fun delete(caller: CallerEntity) {
        viewModelScope.launch { c.callers.delete(caller) }
    }

    suspend fun importPhoto(uri: Uri): String? = c.photos.import(uri)

    fun discardPhoto(name: String?) = c.photos.delete(name)

    data class ContactImportSummary(val scanned: Int, val imported: Int)

    /** Imports all phone-number entries, detects country calling codes, and copies contact photos. */
    suspend fun importAllContacts(): ContactImportSummary {
        val app = getApplication<Application>()
        val defaultIso = withContext(Dispatchers.IO) { ContactsHelper.defaultCountryIso(app) }
        c.callers.fillMissingLocalCountry(defaultIso)
        val entries = withContext(Dispatchers.IO) { ContactsHelper.readAll(app) }
        val candidates = ArrayList<CallerEntity>()
        val seen = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        for (entry in entries) {
            val raw = entry.numberForParsing
            val rawDigits = raw.filter(Char::isDigit)
            val parsed = if (
                defaultIso == "IN" &&
                !raw.startsWith("+") &&
                !raw.startsWith("00") &&
                rawDigits.length == 12 &&
                rawDigits.startsWith("91")
            ) {
                // A common Indian contact-book format stores 91 + 10 digits without the leading '+'.
                PhoneNumbers.Parsed("IN", rawDigits.drop(2))
            } else {
                PhoneNumbers.normalize(raw, defaultIso)
            }
            var national = parsed.national
            // Indian mobile/STD numbers sometimes arrive in local trunk form (0 + 10 digits).
            if (parsed.iso == "IN" && national.length == 11 && national.startsWith('0')) {
                national = national.drop(1)
            }
            val key = "${parsed.iso.uppercase(Locale.ROOT)}:${national.filter(Char::isDigit)}"
            if (national.isBlank() || !seen.add(key)) continue
            val photo = c.photos.importFrom { ContactsHelper.openPhoto(app, entry) }
            candidates += CallerEntity(
                name = entry.name.ifBlank { PhoneNumbers.display(parsed.iso, national) },
                phone = national,
                countryIso = parsed.iso,
                photoFile = photo,
                createdAt = now,
            )
        }
        val inserted = c.callers.importContacts(candidates)
        return ContactImportSummary(scanned = entries.size, imported = inserted)
    }
    /** The contact's name, number and (when Android allows it) photo, copied into app storage. */
    data class ContactPick(val info: ContactInfo, val photoFile: String?)

    suspend fun readContact(uri: Uri): ContactPick? {
        val info = withContext(Dispatchers.IO) { ContactsHelper.read(getApplication(), uri) } ?: return null
        val photo = c.photos.importFrom { ContactsHelper.openPhoto(getApplication(), info) }
        return ContactPick(info, photo)
    }
}
