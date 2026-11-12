package dev.cluvex.strata.core.color

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour maths for the image colour picker. Same formulas and output text as the Color Picker of
 * AUDIO_VIDEO_TOOLS (HEX, RGB, HSL, HSV, CMYK, CIE LAB, Decimal).
 */
object PixelColor {

    data class Format(val label: String, val text: String)

    private data class Hsv(val h: Float, val s: Float, val v: Float, val max: Float, val min: Float, val d: Float)

    private fun hsv(r: Int, g: Int, b: Int): Hsv {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val d = max - min
        var h = 0f
        if (d != 0f) {
            h = when (max) {
                rf -> ((gf - bf) / d) % 6f
                gf -> (bf - rf) / d + 2f
                else -> (rf - gf) / d + 4f
            } * 60f
            if (h < 0f) h += 360f
        }
        return Hsv(h, if (max != 0f) d / max else 0f, max, max, min, d)
    }

    private fun lab(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val rl = lin(r)
        val gl = lin(g)
        val bl = lin(b)
        val x = (rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375) / 0.95047
        val y = rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750
        val z = (rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun n1(v: Double): String = String.format(Locale.ROOT, "%.1f", if (abs(v) < 0.05) 0.0 else v)

    fun hex(r: Int, g: Int, b: Int): String = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)

    fun hex(argb: Int): String = hex((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    /** Parses "#RRGGBB". */
    fun parseHex(text: String): Int? {
        if (!HEX.matches(text)) return null
        return 0xFF000000.toInt() or text.substring(1).toInt(16)
    }

    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    /** Perceived brightness above 0.55 gets dark text, like the original. */
    fun isLight(r: Int, g: Int, b: Int): Boolean = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 > 0.55

    /** Row order: HEX, RGB, HSL, HSV, CMYK, CIE LAB, Decimal. */
    fun formats(r: Int, g: Int, b: Int): List<Format> {
        val c = hsv(r, g, b)
        val l = (c.max + c.min) / 2f
        val sl = if (c.d != 0f) c.d / (1f - abs(2f * l - 1f)) else 0f
        fun p(v: Float) = (v * 100f).roundToInt()
        val k = 1f - c.max
        val cmyk = if (k >= 1f) listOf(0, 0, 0, 100) else listOf(
            p((1f - r / 255f - k) / (1f - k)),
            p((1f - g / 255f - k) / (1f - k)),
            p((1f - b / 255f - k) / (1f - k)),
            p(k),
        )
        val (ll, la, lb) = lab(r, g, b)
        return listOf(
            Format("HEX", hex(r, g, b)),
            Format("RGB", "rgb($r, $g, $b)"),
            Format("HSL", "hsl(${c.h.roundToInt() % 360}, ${p(sl)}%, ${p(l)}%)"),
            Format("HSV", "hsv(${c.h.roundToInt() % 360}, ${p(c.s)}%, ${p(c.v)}%)"),
            Format("CMYK", "cmyk(${cmyk.joinToString("%, ")}%)"),
            Format("CIE LAB", "lab(${n1(ll)}, ${n1(la)}, ${n1(lb)})"),
            Format("Decimal", ((r shl 16) or (g shl 8) or b).toString()),
        )
    }
}
