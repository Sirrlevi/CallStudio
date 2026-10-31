package com.callstudio.app.contacts

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.activity.result.contract.ActivityResultContract
import com.callstudio.app.domain.Countries
import com.callstudio.app.permissions.SystemAccess
import java.io.InputStream
import java.util.Locale

/** System contact picker on a phone-number row. */
class PickPhoneContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

data class ContactInfo(
    val name: String,
    val number: String,
    val contactId: Long?,
    val photoUri: Uri?,
    val normalizedNumber: String? = null,
) {
    /** Prefer Android's E.164 value when it is available for reliable country-code detection. */
    val numberForParsing: String
        get() = normalizedNumber?.takeIf { it.startsWith("+") } ?: number
}

object ContactsHelper {
    private val phoneProjection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
    )

    fun read(context: Context, uri: Uri): ContactInfo? = try {
        context.contentResolver.query(uri, phoneProjection, null, null, null)?.use { c ->
            if (c.moveToFirst()) contactFromCursor(c) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Reads every phone-number row, sorted by contact name, without uploading any data. */
    fun readAll(context: Context): List<ContactInfo> {
        val order = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC, " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} ASC"
        return try {
            val result = ArrayList<ContactInfo>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                phoneProjection,
                null,
                null,
                order,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val item = contactFromCursor(cursor)
                    if (item.name.isNotBlank() && item.numberForParsing.isNotBlank()) result += item
                }
            }
            result
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Prefer SIM/network region over display-language locale (for example, an English UI with an Indian SIM). */
    fun defaultCountryIso(context: Context): String {
        val telephony = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val candidates = listOf(
            runCatching { telephony?.simCountryIso }.getOrNull(),
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            Locale.getDefault().country,
            "IN",
        )
        return candidates.firstNotNullOfOrNull { it?.uppercase(Locale.ROOT)?.takeIf { iso -> Countries.get(iso) != null } }
            ?: "IN"
    }

    private fun contactFromCursor(c: android.database.Cursor): ContactInfo = ContactInfo(
        name = c.getString(0).orEmpty().trim(),
        number = c.getString(1).orEmpty().trim(),
        contactId = if (c.isNull(2)) null else c.getLong(2),
        photoUri = c.getString(3)?.let { Uri.parse(it) },
        normalizedNumber = c.getString(4)?.trim()?.takeIf { it.isNotBlank() },
    )

    /**
     * Opens the contact's own photo (full size when available). The picker alone doesn't share the photo, so
     * this needs the Contacts permission, which the app asks for only when you choose "From contacts".
     */
    fun openPhoto(context: Context, info: ContactInfo): InputStream? {
        val resolver = context.contentResolver
        val id = info.contactId
        if (id != null && SystemAccess.contactsGranted(context)) {
            try {
                val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
                ContactsContract.Contacts.openContactPhotoInputStream(resolver, contactUri, true)?.let { return it }
            } catch (e: Exception) {
                // Fall through to the photo URI.
            }
        }
        val photo = info.photoUri ?: return null
        return try {
            resolver.openInputStream(photo)
        } catch (e: Exception) {
            null
        }
    }
}
