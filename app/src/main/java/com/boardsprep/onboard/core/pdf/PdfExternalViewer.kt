package com.boardsprep.onboard.core.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens a cached/local PDF in an external viewer using a properly configured
 * [FileProvider] and a temporary read grant — never by exposing a raw
 * filesystem path to other apps.
 *
 * If no PDF viewer is installed, the user is shown a clear toast rather than
 * an exception. This is always a *fallback* path; the in-app reader is the
 * primary experience.
 */
object PdfExternalViewer {

    /**
     * Open [file] in an external PDF viewer. [originalUrl] is used only as a
     * last-resort fallback for the rare case where the file is not yet cached
     * locally but the caller still wants to try the original remote URL.
     */
    fun open(context: Context, file: File?, originalUrl: String?, authoritySuffix: String = ".fileprovider") {
        val authority = context.packageName + authoritySuffix
        val target: Uri
        val intent: Intent
        if (file != null && file.exists()) {
            target = FileProvider.getUriForFile(context, authority, file)
            intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(target, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else if (!originalUrl.isNullOrEmpty()) {
            target = Uri.parse(originalUrl)
            intent = Intent(Intent.ACTION_VIEW, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Toast.makeText(context, "No document available to open.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No PDF viewer app installed to open this document.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
