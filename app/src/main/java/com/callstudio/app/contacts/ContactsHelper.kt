package com.callstudio.app.contacts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Uses the system contact picker on a phone-number row. The picker hands the app one-time read access to the
 * chosen row, so READ_CONTACTS is never requested and the rest of the address book stays private.
 */
class PickPhoneContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

data class ContactInfo(val name: String, val number: String, val photoUri: Uri?)

object ContactsHelper {
    fun read(context: Context, uri: Uri): ContactInfo? = try {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                ContactInfo(
                    name = c.getString(0).orEmpty(),
                    number = c.getString(1).orEmpty(),
                    photoUri = c.getString(2)?.let { Uri.parse(it) },
                )
            } else null
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}
