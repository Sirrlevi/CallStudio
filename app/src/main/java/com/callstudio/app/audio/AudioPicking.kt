package com.callstudio.app.audio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat

/** System ringtone picker. Returns the chosen sound, or null when the user cancelled. */
class PickRingtoneContract : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose a ringtone")
            if (input != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return IntentCompat.getParcelableExtra(intent, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    }
}

object AudioFiles {
    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun ringtoneTitle(context: Context, uri: Uri): String? = try {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
    } catch (e: Exception) {
        null
    }

    /** Keeps read access to a Storage Access Framework file across restarts. */
    fun persist(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Some providers don't offer persistable grants; playback then works until access lapses.
        }
    }

    fun release(context: Context, uriString: String?) {
        if (uriString == null) return
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Nothing was persisted for this URI.
        }
    }
}
