package dev.cluvex.strata.core.shell

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Category names of the shell commands, in display order. They share the "shell_ui" preferences with the shell screen,
 * which also keeps the folded state there (that one is not part of a backup).
 */
class ShellCategoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shell_ui", Context.MODE_PRIVATE)
    private val names = ListSerializer(String.serializer())

    fun load(): List<String> =
        runCatching { Json.decodeFromString(names, prefs.getString(KEY, "[]") ?: "[]") }.getOrDefault(emptyList())

    /** The stored order, plus any category named by one of [commands] that is not stored yet. */
    fun merged(commands: List<SavedCommand>): List<String> {
        val result = load().toMutableList()
        for (c in commands) if (c.category.isNotEmpty() && c.category !in result) result += c.category
        return result
    }

    /** Adds the categories that are not here yet at the end, in the given order; the categories already here keep their place. */
    fun addAll(categories: List<String>) {
        val current = load()
        val added = categories.map { it.trim() }.filter { it.isNotEmpty() && it !in current }.distinct()
        if (added.isEmpty()) return
        prefs.edit().putString(KEY, Json.encodeToString(names, current + added)).apply()
    }

    private companion object {
        const val KEY = "categories"
    }
}
