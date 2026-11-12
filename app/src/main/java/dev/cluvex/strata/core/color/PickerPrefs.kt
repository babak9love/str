package dev.cluvex.strata.core.color

/** Settings and history of the image colour picker (loupe zoom, arrow-pad step, auto-copy, picked colours). */
object PickerPrefs {
    const val MAX_HISTORY = 30

    val ZOOMS = listOf(1, 2, 4, 8, 16, 32)
    val STEPS = listOf(1, 2, 5, 10)

    enum class AutoCopy { OFF, HEX, RGB, HSL }

    data class Settings(val zoom: Int = 8, val step: Int = 1, val autoCopy: AutoCopy = AutoCopy.OFF) {
        fun encode(): String = "$zoom,$step,${autoCopy.name}"
    }

    fun decodeSettings(text: String?): Settings {
        val parts = text.orEmpty().split(',')
        val zoom = parts.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in ZOOMS } ?: 8
        val step = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in STEPS } ?: 1
        val copy = AutoCopy.entries.firstOrNull { it.name == parts.getOrNull(2)?.trim() } ?: AutoCopy.OFF
        return Settings(zoom, step, copy)
    }

    /** Newest first, no duplicates, opaque RRGGBB only. */
    fun pushHistory(history: List<Int>, argb: Int): List<Int> {
        val c = argb or 0xFF000000.toInt()
        return (listOf(c) + history.filterNot { it == c }).take(MAX_HISTORY)
    }

    fun encodeHistory(colors: List<Int>): String = colors.joinToString(",") { "%06X".format(java.util.Locale.ROOT, it and 0xFFFFFF) }

    fun decodeHistory(text: String?): List<Int> =
        text.orEmpty().split(',').mapNotNull { s -> s.trim().takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000.toInt() or it.toInt() } }
            .distinct().take(MAX_HISTORY)
}
