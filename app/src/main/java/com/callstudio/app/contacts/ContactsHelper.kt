package com.callstudio.app.contacts

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract
import com.callstudio.app.permissions.SystemAccess
import java.io.InputStream

/** System contact picker on a phone-number row. */
class PickPhoneContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

data class ContactInfo(val name: String, val number: String, val contactId: Long?, val photoUri: Uri?)

object ContactsHelper {
    fun read(context: Context, uri: Uri): ContactInfo? = try {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                ContactInfo(
                    name = c.getString(0).orEmpty(),
                    number = c.getString(1).orEmpty(),
                    contactId = if (c.isNull(2)) null else c.getLong(2),
                    photoUri = c.getString(3)?.let { Uri.parse(it) },
                )
            } else null
        }
    } catch (e: Exception) {
        null
    }

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
