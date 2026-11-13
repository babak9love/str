package dev.cluvex.strata.core.plugin

import dev.cluvex.strata.R
import dev.cluvex.strata.core.fs.FileAccess
import dev.cluvex.strata.core.fs.FsUtil
import dev.cluvex.strata.core.fs.LocalFileAccess
import dev.cluvex.strata.core.i18n.tr
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object PluginFiles {
    fun copyCapped(input: InputStream, output: OutputStream, max: Long): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return total
            total += n
            if (total > max) throw IOException(tr(R.string.plugin_file_too_large, max shr 20))
            output.write(buf, 0, n)
        }
    }

    /**
     * Saves a plugin's result. "Replace" puts it where the original was; when the plugin changed the format
     * (a name with another extension, like song.mp3 -> song_encoded.opus) the result takes the original's place under
     * the original's name with the new extension, so the name never claims a format the content doesn't have.
     */
    fun save(result: File, original: File, suggestedName: String?, replace: Boolean, access: FileAccess = LocalFileAccess): File {
        val dir = original.parentFile ?: throw IOException(tr(R.string.plugin_no_folder))
        if (!replace) {
            val target = FsUtil.uniqueSibling(access.child(dir, suggestedName ?: original.name), isDirectory = false, access = access)
            write(result, target, access)
            return target
        }
        val newExt = suggestedName?.let(::extensionOf)
        val oldExt = extensionOf(original.name)
        if (newExt == null || newExt.equals(oldExt, ignoreCase = true)) {
            write(result, original, access)
            return original
        }
        val base = if (oldExt == null) original.name else original.name.dropLast(oldExt.length + 1)
        val target = FsUtil.uniqueSibling(access.child(dir, "$base.$newExt"), isDirectory = false, access = access)
        write(result, target, access)
        FsUtil.deleteRecursively(original, access)
        return target
    }

    private fun write(result: File, target: File, access: FileAccess) {
        result.inputStream().use { input -> access.openOutput(target).use { input.copyTo(it) } }
    }

    private fun extensionOf(name: String): String? {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else null
    }
}
