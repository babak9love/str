package dev.cluvex.strata.core.open

import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.cluvex.strata.feature.explorer.ShareMode

/** Reads the files another app shared to Strata, and what the user chose to do with them. */
object IncomingShare {
    const val EXTRA_MODE = "dev.cluvex.strata.extra.SHARE_MODE"

    @Suppress("DEPRECATION")
    fun uris(intent: Intent?): List<Uri> {
        val action = intent?.action ?: return emptyList()
        val found = when (action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty().filterNotNull()
            else -> emptyList()
        }
        // Some apps put the files only in the clip data, not in EXTRA_STREAM.
        val all = found.ifEmpty {
            if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) emptyList()
            else intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        }
        return all.filter { it.scheme == "content" || it.scheme == "file" }.distinct()
    }

    fun mode(intent: Intent?): ShareMode? =
        intent?.getStringExtra(EXTRA_MODE)?.let { name -> ShareMode.values().firstOrNull { it.name == name } }
}
