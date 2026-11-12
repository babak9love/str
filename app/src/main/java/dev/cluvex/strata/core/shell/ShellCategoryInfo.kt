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
        /** A pleasant colour of the given hue (degrees): clearly coloured, neither too pale nor too dark. */
        private fun hueColor(hue: Float, saturation: Float = 0.68f, value: Float = 0.88f): Int =
            android.graphics.Color.HSVToColor(floatArrayOf(Math.floorMod(hue.toInt(), 360).toFloat(), saturation, value))

        private fun hueOf(color: Int): Float {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color, hsv)
            return hsv[0]
        }

        /** Distance between two hues on the colour wheel, 0 to 180 degrees. */
        private fun hueGap(a: Float, b: Float): Float {
            val d = Math.abs(a - b) % 360f
            return if (d > 180f) 360f - d else d
        }

        /**
         * A colour for a new category: any hue, so there is no fixed list. Of several random candidates it takes the one whose
         * hue is furthest from the colours in [used], so a new category does not look like an existing one.
         */
        fun randomColor(used: Collection<Int> = emptyList()): Int {
            val usedHues = used.map { hueOf(it) }
            val candidates = List(12) {
                hueColor(Random.nextFloat() * 360f, Random.nextFloat() * 0.25f + 0.55f, Random.nextFloat() * 0.15f + 0.8f)
            }
            if (usedHues.isEmpty()) return candidates.first()
            return candidates.maxByOrNull { c -> val h = hueOf(c); usedHues.minOf { hueGap(h, it) } } ?: candidates.first()
        }

        /** The colour shown for a category that has none stored (e.g. made before colours existed): stable for its name. */
        fun fallbackColor(name: String): Int = hueColor(Math.floorMod(name.hashCode(), 360).toFloat())

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
