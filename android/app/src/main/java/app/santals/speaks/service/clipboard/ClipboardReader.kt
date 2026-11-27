package app.santals.speaks.service.clipboard

import android.content.ClipboardManager
import android.content.Context

/**
 * Reads the clipboard only when the user taps Paste. The app never listens to clipboard changes
 * in the background, which Android 10+ restricts anyway.
 */
object ClipboardReader {

    /** Returns the first text item on the clipboard, untrimmed so the original text is preserved. */
    fun readPlainText(context: Context): String? {
        return try {
            val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
            if (!manager.hasPrimaryClip()) return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        }
    }
}
