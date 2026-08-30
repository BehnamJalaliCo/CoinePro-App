package com.coinepro.feature.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.coinepro.core.diagnostics.DiagnosticExport
import com.coinepro.core.diagnostics.ExportOutcome
import java.io.File

/**
 * Getting the report off the phone.
 *
 * This is the request the owner actually made — "so I can export the output and hand it to you or
 * to any developer" — and it is deliberately three separate acts rather than one, because they fail
 * and succeed in different places:
 *
 *  * **Share** hands the file to another app on the handset. This is the one to use when there is a
 *    developer in a chat window: two taps and the file is in the conversation.
 *  * **Save** writes it wherever the system document picker points — a drive, downloads, a mail
 *    draft. The app keeps no copy of a document it has no business keeping.
 *  * **Copy** puts the text on the clipboard. Not a file at all, and the right answer when the log
 *    is short and the destination is a message box.
 *
 * ### The file is written to the one directory the app is willing to hand out of
 *
 * `cacheDir/shared/` is what the application's `FileProvider` exposes, and nothing else is exposed.
 * A provider scoped to `files/` would offer the reader's cached quotes and their journal export to
 * any app that could be persuaded to ask. The URI is granted read permission for one intent rather
 * than the file being made readable: the app the operator picked can open it, nothing else can.
 *
 * ### Nothing in the file is a secret
 *
 * Not a promise made here — a property of what goes in. [com.coinepro.core.diagnostics.AppLog]
 * scrubs every entry before it exists, install ids and push tokens are masked at their boundary,
 * hostnames are masked, and [DiagnosticExport.render] scrubs the assembled text once more. This
 * file only moves bytes.
 */
internal object DiagnosticHandoff {

    /**
     * Writes the report and opens the share sheet.
     *
     * The previous export is deleted before the new one is written. Otherwise a folder of every
     * report anybody ever shared accumulates inside the app, invisible, until a phone runs out of
     * room — and this is a directory a reader cannot see to clean.
     */
    fun share(context: Context, report: String, atEpochMillis: Long): ExportOutcome = runCatching {
        val directory = File(context.cacheDir, SHARED_DIRECTORY).apply {
            mkdirs()
            listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.forEach(File::delete)
        }
        val file = File(directory, DiagnosticExport.fileName(atEpochMillis))
        file.writeText(report)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = DiagnosticExport.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            // The subject is what a mail client puts on the line an operator will search for later.
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        ExportOutcome.SHARED
    }.getOrDefault(ExportOutcome.FAILED)

    /** Writes the report into the document the reader chose. Nothing is retained by the app. */
    fun save(context: Context, destination: Uri, report: String): ExportOutcome = runCatching {
        context.contentResolver.openOutputStream(destination)?.use { stream ->
            stream.write(report.toByteArray(Charsets.UTF_8))
        } ?: return ExportOutcome.FAILED
        ExportOutcome.SAVED
    }.getOrDefault(ExportOutcome.FAILED)

    /**
     * The clipboard.
     *
     * Android stopped showing its own "copied" confirmation in 13, so the panel says so itself —
     * without one, a copy is completely invisible and gets pressed three times.
     */
    fun copy(context: Context, label: String, text: String): Boolean = runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    }.getOrDefault(false)

    private const val SHARED_DIRECTORY = "shared"
    private const val FILE_PREFIX = "coinepro-diagnostics-"
}
