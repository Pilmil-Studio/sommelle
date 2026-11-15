package fr.sommelle.core.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** Contraste WCAG des paires texte / fond du thème : 4,5:1 pour le texte, 3:1 pour les bordures d'état. */
class ContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val C = SommelleColors

    @Test
    fun `les paires texte et fond declarees atteignent 4,5 pour 1`() {
        val pairs = mapOf(
            "Ink / Cream" to (C.Ink to C.Cream),
            "Ink / Paper" to (C.Ink to C.Paper),
            "InkMuted / Cream" to (C.InkMuted to C.Cream),
            "InkMuted / Paper" to (C.InkMuted to C.Paper),
            "InkMuted / Lavender" to (C.InkMuted to C.Lavender),
            "Night / Lavender" to (C.Night to C.Lavender),
            "Cream / Night" to (C.Cream to C.Night),
            "LavenderOnNight / Night" to (C.LavenderOnNight to C.Night),
            "Ink / Amber" to (C.Ink to C.Amber),
            "Sand / Ember" to (C.Sand to C.Ember),
            "Sand / EmberSurface" to (C.Sand to C.EmberSurface),
            "Sand / EmberContainer" to (C.Sand to C.EmberContainer),
            "SandMuted / Ember" to (C.SandMuted to C.Ember),
            "SandMuted / EmberSurface" to (C.SandMuted to C.EmberSurface),
            "SandMuted / EmberContainer" to (C.SandMuted to C.EmberContainer),
            "Ember / Sand" to (C.Ember to C.Sand),
            "Ember / AmberDim" to (C.Ember to C.AmberDim),
        )
        pairs.forEach { (name, p) -> assertTrue(ratio(p.first, p.second) >= 4.5, "$name : ${ratio(p.first, p.second)}") }
    }

    @Test
    fun `les bordures de choix atteignent 3 pour 1`() {
        val borders = mapOf(
            "ChipBorder / Cream" to (C.ChipBorder to C.Cream),
            "ChipBorder / Paper" to (C.ChipBorder to C.Paper),
            "EmberOutline / Ember" to (C.EmberOutline to C.Ember),
            "EmberOutline / EmberSurface" to (C.EmberOutline to C.EmberSurface),
            "EmberOutline / EmberContainer" to (C.EmberOutline to C.EmberContainer),
        )
        borders.forEach { (name, p) -> assertTrue(ratio(p.first, p.second) >= 3.0, "$name : ${ratio(p.first, p.second)}") }
    }
}
