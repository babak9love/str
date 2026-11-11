package dev.cluvex.strata.core.shell

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Categories of the shell commands: their names in display order, and each one's description and colour. They share the
 * "shell_ui" preferences with the shell screen, which also keeps the folded state there (that one is not part of a backup).
 */
class ShellCategoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shell_ui", Context.MODE_PRIVATE)
    private val names = ListSerializer(String.serializer())
    private val infoSerializer = ListSerializer(ShellCategoryInfo.serializer())

    fun load(): List<String> =
        runCatching { Json.decodeFromString(names, prefs.getString(KEY, "[]") ?: "[]") }.getOrDefault(emptyList())

    /** Description and colour by category name. */
    fun loadInfo(): Map<String, ShellCategoryInfo> =
        runCatching { Json.decodeFromString(infoSerializer, prefs.getString(INFO_KEY, "[]") ?: "[]") }
            .getOrDefault(emptyList()).associateBy { it.name }

    /** The stored order, plus any category named by one of [commands] that is not stored yet. */
    fun merged(commands: List<SavedCommand>): List<String> {
        val result = load().toMutableList()
        for (c in commands) if (c.category.isNotEmpty() && c.category !in result) result += c.category
        return result
    }

    /** [merged] with each category's description and colour; a category without a stored colour gets its fallback one. */
    fun mergedInfo(commands: List<SavedCommand>): List<ShellCategoryInfo> {
        val info = loadInfo()
        return merged(commands).map { n ->
            val i = info[n] ?: ShellCategoryInfo(n)
            i.copy(color = if (i.color != 0) i.color else ShellCategoryInfo.fallbackColor(n))
        }
    }

    /**
     * Adds the categories that are not here yet at the end, in the given order, with their description and colour (a missing
     * colour is chosen at random); the categories already here keep their place and their look.
     */
    fun addAll(categories: List<ShellCategoryInfo>) {
        val current = load()
        val info = loadInfo().toMutableMap()
        val added = categories.map { it.copy(name = it.name.trim()) }.filter { it.name.isNotEmpty() && it.name !in current }.distinctBy { it.name }
        if (added.isEmpty()) return
        for (c in added) {
            val color = if (c.color != 0) c.color else ShellCategoryInfo.randomColor(info.values.map { it.color })
            info[c.name] = c.copy(color = color)
        }
        prefs.edit()
            .putString(KEY, Json.encodeToString(names, current + added.map { it.name }))
            .putString(INFO_KEY, Json.encodeToString(infoSerializer, info.values.toList()))
            .apply()
    }

    private companion object {
        const val KEY = "categories"
        const val INFO_KEY = "category_info"
    }
}
