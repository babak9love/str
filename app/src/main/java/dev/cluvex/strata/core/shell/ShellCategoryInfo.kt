package dev.cluvex.strata.core.shell

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.random.Random

/** A shell command category: its name, an optional description and its colour (ARGB, 0 = not set yet). */
@Serializable
data class ShellCategoryInfo(val name: String, val description: String = "", val color: Int = 0) {
    companion object {
        private val PALETTE = intArrayOf(
            0xFFE5484D.toInt(), // red
            0xFF3E9BD6.toInt(), // blue
            0xFF30A46C.toInt(), // green
            0xFFF5A524.toInt(), // amber
            0xFF8E4EC6.toInt(), // purple
            0xFF12A594.toInt(), // teal
            0xFFE93D82.toInt(), // pink
            0xFFF76B15.toInt(), // orange
            0xFF6E56CF.toInt(), // indigo
            0xFF8DB600.toInt(), // lime
        )

        /** A colour for a new category: one from the palette that no other category uses yet, else any of them. */
        fun randomColor(used: Collection<Int> = emptyList()): Int {
            val free = PALETTE.filter { it !in used }
            return (free.ifEmpty { PALETTE.toList() }).random(Random.Default)
        }

        /** The colour shown for a category that has none stored (e.g. made before colours existed): stable for its name. */
        fun fallbackColor(name: String): Int = PALETTE[Math.floorMod(name.hashCode(), PALETTE.size)]

        /** One entry of a "shellCategories" list: an object with a name, or (older files) just the name. */
        fun parse(element: JsonElement): ShellCategoryInfo? = when (element) {
            is JsonObject -> {
                val name = (element["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isEmpty()) null
                else ShellCategoryInfo(
                    name = name,
                    description = (element["description"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
                    color = (element["color"] as? JsonPrimitive)?.intOrNull ?: 0,
                )
            }
            is JsonPrimitive -> if (element.isString) element.content.trim().ifEmpty { null }?.let { ShellCategoryInfo(it) } else null
            else -> null
        }
    }
}
