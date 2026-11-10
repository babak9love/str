package dev.cluvex.strata.core.shell

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A command template the user added to the templates list of the command editor. */
@Serializable
data class ShellTemplate(val name: String, val command: String)

/**
 * The user's own templates, in the order they were added. They share the "shell_ui" preferences with the shell
 * screen and are part of a settings backup (not of the commands export, which is about the commands).
 */
class ShellTemplateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shell_ui", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ShellTemplate.serializer())

    fun load(): List<ShellTemplate> =
        runCatching { json.decodeFromString(serializer, prefs.getString(KEY, "[]") ?: "[]") }.getOrDefault(emptyList())

    private fun save(templates: List<ShellTemplate>): List<ShellTemplate> {
        prefs.edit().putString(KEY, json.encodeToString(serializer, templates)).apply()
        return templates
    }

    /** Adds [template]; one with the same name is replaced where it stands. Blank names or commands are ignored. */
    fun add(template: ShellTemplate): List<ShellTemplate> {
        val t = ShellTemplate(template.name.trim(), template.command)
        val current = load()
        if (t.name.isEmpty() || t.command.isBlank()) return current
        val index = current.indexOfFirst { it.name == t.name }
        return save(if (index >= 0) current.toMutableList().also { it[index] = t } else current + t)
    }

    fun remove(name: String): List<ShellTemplate> = save(load().filter { it.name != name })

    /** Templates from a backup: the same name is replaced, the others are kept, new ones go at the end. */
    fun merge(incoming: List<ShellTemplate>): List<ShellTemplate> = incoming.fold(load()) { acc, t ->
        val clean = ShellTemplate(t.name.trim(), t.command)
        if (clean.name.isEmpty() || clean.command.isBlank()) acc
        else {
            val index = acc.indexOfFirst { it.name == clean.name }
            if (index >= 0) acc.toMutableList().also { it[index] = clean } else acc + clean
        }
    }.let { save(it) }

    private companion object {
        const val KEY = "user_templates"
    }
}
