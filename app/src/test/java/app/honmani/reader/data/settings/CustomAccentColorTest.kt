package app.honmani.reader.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CustomAccentColorTest {
    private fun channels(color: Long) = Triple((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)

    @Test
    fun hueZeroIsRedAndOneTwentyIsGreen() {
        val (r, g, b) = channels(customAccentColor(0f))
        assertTrue(r > g && r > b)
        val (r2, g2, b2) = channels(customAccentColor(120f))
        assertTrue(g2 > r2 && g2 > b2)
    }

    @Test
    fun isOpaqueAndWrapsAround() {
        assertEquals(0xFFL, customAccentColor(200f) shr 24)
        assertEquals(customAccentColor(0f), customAccentColor(360f))
        assertEquals(customAccentColor(350f), customAccentColor(-10f))
    }

    @Test
    fun everyHueStandsOutOnTheDarkBackground() {
        // WCAG contrast against the dark theme's background (about #141218); 3:1 is the minimum
        // for buttons and other large elements.
        val background = luminance(0x141218)
        for (hue in 0 until 360 step 5) {
            val accent = luminance(customAccentColor(hue.toFloat()))
            val contrast = (accent + 0.05) / (background + 0.05)
            assertTrue("hue $hue: contrast $contrast", contrast >= 3.0)
        }
    }

    private fun luminance(color: Long): Double {
        val (r, g, b) = channels(color)
        fun linear(c: Long): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
}
